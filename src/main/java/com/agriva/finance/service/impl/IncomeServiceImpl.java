package com.agriva.finance.service.impl;

import com.agriva.finance.model.dto.request.IncomeRequest;
import com.agriva.finance.model.dto.request.IncomeSubmissionEvent;
import com.agriva.finance.model.dto.response.BatchStatusResponse;
import com.agriva.finance.model.dto.response.BatchUploadResponse;
import com.agriva.finance.model.dto.response.IncomeResponse;
import com.agriva.finance.model.dto.response.IncomeStagingResponse;
import com.agriva.finance.model.dto.response.IncomeSubmissionAck;
import com.agriva.finance.model.enums.StagedStatus;
import com.agriva.finance.utils.BatchParsedRow;
import com.agriva.finance.utils.IncomeExcelParser;
import com.agriva.shared.exception.DuplicateIncomeException;
import com.agriva.shared.exception.IncomeNotFoundException;
import com.agriva.shared.exception.ResourceNotFoundException;
import com.agriva.finance.kafka.producer.IncomeProducer;
import com.agriva.finance.mapper.IncomeMapper;
import com.agriva.shared.models.dto.CursorPageResponse;
import com.agriva.finance.model.entity.Income;
import com.agriva.finance.model.entity.StagedIncome;
import com.agriva.identity.models.entity.User;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.finance.repository.IncomeRepository;
import com.agriva.finance.repository.StagedIncomeRepository;
import com.agriva.finance.service.IncomeService;
import com.agriva.shared.models.ref.UserRef;
import com.agriva.shared.utils.CursorCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncomeServiceImpl implements IncomeService {

    private final IncomeRepository incomeRepo;
    private final StagedIncomeRepository stagedRepo;
    private final AuthRepository authRepository;
    private final IncomeProducer incomeProducer;
    private final IncomeExcelParser excelParser;
    private final IncomeMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public IncomeSubmissionAck submitIncome(IncomeRequest request, UUID farmId, UUID userId, String email) {
        UUID submissionId = UUID.randomUUID();
        // Single-submit: batchId and rowNumber stay null.
        IncomeSubmissionEvent event = new IncomeSubmissionEvent(
                submissionId, farmId, userId, email, serialize(request),
                LocalDateTime.now(), null, null);
        incomeProducer.publish(event);
        log.info("Income submission {} queued for farm {}", submissionId, farmId);
        return new IncomeSubmissionAck(submissionId, farmId, "QUEUED", LocalDateTime.now());
    }

    @Override
    public BatchUploadResponse bulkUpload(MultipartFile file, UUID farmId, UUID userId, String email) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required");
        }

        List<BatchParsedRow<IncomeRequest>> rows;
        try {
            rows = excelParser.parse(file.getInputStream());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read Excel file: " + e.getMessage(), e);
        }

        UUID batchId = UUID.randomUUID();
        BatchUploadResponse response = new BatchUploadResponse();
        response.setBatchId(batchId);
        response.setTotalRows(rows.size());

        for (BatchParsedRow<IncomeRequest> row : rows) {
            if (row.isError()) {
                response.setFailed(response.getFailed() + 1);
                response.getResults().add(new BatchUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", row.error()));
                continue;
            }

            UUID submissionId = UUID.randomUUID();
            incomeProducer.publish(new IncomeSubmissionEvent(
                    submissionId, farmId, userId, email,
                    serialize(row.request()), LocalDateTime.now(),
                    batchId, row.rowNumber()));

            response.setQueued(response.getQueued() + 1);
            response.getResults().add(new BatchUploadResponse.RowResult(
                    row.rowNumber(), submissionId, "QUEUED", null));
        }

        log.info("Bulk income upload {} for farm {}: total={}, queued={}, failed={}",
                batchId, farmId, response.getTotalRows(),
                response.getQueued(), response.getFailed());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public BatchStatusResponse bulkStatus(UUID batchId, UUID farmId) {
        List<StagedIncome> staged =
                stagedRepo.findByFarmIdAndBatchIdOrderByRowNumberAsc(farmId, batchId);

        if (staged.isEmpty()) {
            throw new ResourceNotFoundException("Batch not found: " + batchId);
        }

        int pending = 0, moved = 0, failed = 0;
        List<BatchStatusResponse.FailedRow> failedRows = new ArrayList<>();

        for (StagedIncome s : staged) {
            switch (s.getStatus()) {
                case PENDING, RETRYING -> pending++;
                case MOVED  -> moved++;
                case FAILED -> {
                    failed++;
                    failedRows.add(new BatchStatusResponse.FailedRow(
                            s.getRowNumber(), s.getId(), s.getValidationErrors()));
                }
            }
        }

        return BatchStatusResponse.builder()
                .batchId(batchId)
                .total(staged.size())
                .pending(pending)
                .moved(moved)
                .failed(failed)
                .failedRows(failedRows.isEmpty() ? null : failedRows)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IncomeResponse> listIncome(UUID farmId, Pageable pageable) {
        Page<Income> page = incomeRepo.findByFarmIdLive(farmId, pageable);
        Map<UUID, UserRef> userById = lookupUsers(page.getContent());
        return page.map(i -> mapper.toResponse(i, userById));
    }

    @Override
    @Transactional(readOnly = true)
    public IncomeResponse getIncome(UUID id, UUID farmId) {
        Income income = incomeRepo.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new IncomeNotFoundException("Income not found with ID: " + id));
        return mapper.toResponse(income, lookupUsers(List.of(income)));
    }

    @Override
    @Transactional
    public IncomeResponse updateIncome(UUID id, IncomeRequest request, UUID farmId) {
        Income income = incomeRepo.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new IncomeNotFoundException("Income not found with ID: " + id));

        if (incomeRepo.existsByFarmIdAndAmountReceivedAndIncomeDateAndReasonLive(
                farmId, request.getAmountReceived(), request.getIncomeDate(), request.getReason())
                && !(income.getAmountReceived().compareTo(request.getAmountReceived()) == 0
                && income.getIncomeDate().equals(request.getIncomeDate())
                && Objects.equals(income.getReason(), request.getReason()))) {
            throw new DuplicateIncomeException("Another income with the same amount, date and reason already exists");
        }
        income.setReason(request.getReason());
        income.setQuantity(request.getQuantity());
        income.setAmountReceived(request.getAmountReceived());
        income.setExpectedAmount(request.getExpectedAmount());
        income.setReceipt(request.getReceipt());
        income.setCurrency(request.getCurrency());
        income.setCategory(request.getCategory());
        income.setIncomeDate(request.getIncomeDate());
        income.setTransactionId(request.getTransactionId());
        income.calculateDueBalance();

        Income saved = incomeRepo.save(income);
        log.info("Income {} updated for farm {}", id, farmId);
        return mapper.toResponse(saved, lookupUsers(List.of(saved)));
    }

    @Override
    @Transactional
    public void deleteIncome(UUID id, UUID farmId) {
        Income income = incomeRepo.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new IncomeNotFoundException("Income not found with ID: " + id));
        income.setDeletedAt(LocalDateTime.now());
        incomeRepo.save(income);
        log.info("Soft-deleted income {} from farm {}", id, farmId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IncomeStagingResponse> listStaging(UUID farmId, String status, Pageable pageable) {
        Page<StagedIncome> page;
        if (status != null && !status.isBlank()) {
            page = stagedRepo.findByFarmIdAndStatus(farmId,
                    StagedStatus.valueOf(status.toUpperCase()), pageable);
        } else {
            page = stagedRepo.findByFarmId(farmId, pageable);
        }
        return page.map(mapper::toStaging);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> stagingStats(UUID farmId) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (StagedStatus s : StagedStatus.values()) {
            out.put(s.name(), stagedRepo.countByFarmIdAndStatus(farmId, s));
        }
        return out;
    }

    private String serialize(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize income payload", e);
        }
    }

    private Map<UUID, UserRef> lookupUsers(List<Income> incomes) {
        Set<UUID> userIds = incomes.stream()
                .map(Income::getCreatedByUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) return Map.of();
        return authRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> new UserRef(
                                u.getId(), u.getEmail(), u.getUsername(),
                                u.getRole(), u.getFarmId(),
                                u.getCreatedAt(), u.getUpdatedAt()),
                        (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<IncomeResponse> listIncomeByCursor(UUID farmId, String cursor, int size) {
        int cappedSize = Math.clamp(size, 1, 100);

        Pageable limit = PageRequest.of(0, cappedSize + 1);

        java.time.LocalDate cursorDate = null;
        java.util.UUID cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            CursorCodec.Decoded d = CursorCodec.decodeDate(cursor);
            cursorDate = (java.time.LocalDate) d.sortValue();
            cursorId = d.id();
        }

        List<Income> rows = incomeRepo.findCursorPage(farmId, cursorDate, cursorId, limit);

        boolean hasNext = rows.size() > cappedSize;
        List<Income> page = hasNext ? rows.subList(0, cappedSize) : rows;

        Map<UUID, UserRef> userById = lookupUsers(page);
        List<IncomeResponse> dtos = page.stream()
                .map(i -> mapper.toResponse(i, userById))
                .toList();

        String nextCursor = hasNext
                ? CursorCodec.encode(page.get(page.size() - 1).getIncomeDate(),
                page.get(page.size() - 1).getId())
                : null;

        return new CursorPageResponse<>(dtos, nextCursor, hasNext, dtos.size());
    }
}