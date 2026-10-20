package com.agriva.service.impl;

import com.agriva.exception.DuplicateIncomeException;
import com.agriva.exception.IncomeNotFoundException;
import com.agriva.kafka.producer.IncomeProducer;
import com.agriva.mapper.IncomeMapper;
import com.agriva.model.dto.income.*;
import com.agriva.model.entity.Income;
import com.agriva.model.entity.StagedIncome;
import com.agriva.model.entity.StagedIncomeStatus;
import com.agriva.model.entity.User;
import com.agriva.repository.AuthRepository;
import com.agriva.repository.IncomeRepository;
import com.agriva.repository.StagedIncomeRepository;
import com.agriva.service.IncomeService;
import com.agriva.utils.IncomeExcelParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
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
        IncomeSubmissionEvent event = new IncomeSubmissionEvent(
                submissionId, farmId, userId, email, serialize(request), LocalDateTime.now());
        incomeProducer.publish(event);
        log.info("Income submission {} queued for farm {}", submissionId, farmId);
        return new IncomeSubmissionAck(submissionId, farmId, "QUEUED", LocalDateTime.now());
    }

    @Override
    public IncomeBulkUploadResponse bulkUpload(MultipartFile file, UUID farmId, UUID userId, String email) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required");
        }

        List<IncomeExcelParser.ParsedRow> rows;
        try {
            rows = excelParser.parse(file.getInputStream());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read Excel file: " + e.getMessage(), e);
        }

        IncomeBulkUploadResponse response = new IncomeBulkUploadResponse();
        response.setTotalRows(rows.size());

        for (IncomeExcelParser.ParsedRow row : rows) {
            if (row.isError()) {
                response.setFailed(response.getFailed() + 1);
                response.getResults().add(new IncomeBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", row.error()));
                continue;
            }

            UUID submissionId = UUID.randomUUID();
            incomeProducer.publish(new IncomeSubmissionEvent(
                    submissionId, farmId, userId, email,
                    serialize(row.request()), LocalDateTime.now()));

            response.setQueued(response.getQueued() + 1);
            response.getResults().add(new IncomeBulkUploadResponse.RowResult(
                    row.rowNumber(), submissionId, "QUEUED", null));
        }

        log.info("Bulk income upload for farm {}: total={}, queued={}, failed={}",
                farmId, response.getTotalRows(), response.getQueued(), response.getFailed());
        return response;
    }

    @Override
    public Page<IncomeResponse> listIncome(UUID farmId, Pageable pageable) {
        Page<Income> page = incomeRepo.findByFarmId(farmId, pageable);
        Map<UUID, User> userById = lookupUsers(page.getContent());
        return page.map(i -> mapper.toResponse(i, userById));
    }

    @Override
    public IncomeResponse getIncome(UUID id, UUID farmId) {
        Income income = incomeRepo.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new IncomeNotFoundException("Income not found with ID: " + id));
        return mapper.toResponse(income, lookupUsers(List.of(income)));
    }

    @Override
    @Transactional
    public IncomeResponse updateIncome(UUID id, IncomeRequest request, UUID farmId) {
        Income income = incomeRepo.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new IncomeNotFoundException("Income not found with ID: " + id));

        if (incomeRepo.existsByFarmIdAndAmountReceivedAndIncomeDateAndReason(
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
        Income income = incomeRepo.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new IncomeNotFoundException("Income not found with ID: " + id));
        incomeRepo.delete(income);
        log.info("Income {} deleted from farm {}", id, farmId);
    }

    @Override
    public Page<IncomeStagingResponse> listStaging(UUID farmId, String status, Pageable pageable) {
        Page<StagedIncome> page;
        if (status != null && !status.isBlank()) {
            page = stagedRepo.findByFarmIdAndStatus(farmId,
                    StagedIncomeStatus.valueOf(status.toUpperCase()), pageable);
        } else {
            page = stagedRepo.findByFarmId(farmId, pageable);
        }
        return page.map(mapper::toStaging);
    }

    @Override
    public Map<String, Long> stagingStats(UUID farmId) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (StagedIncomeStatus s : StagedIncomeStatus.values()) {
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

    private Map<UUID, User> lookupUsers(List<Income> incomes) {
        Set<UUID> userIds = incomes.stream()
                .map(Income::getCreatedByUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) return Map.of();
        return authRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }
}