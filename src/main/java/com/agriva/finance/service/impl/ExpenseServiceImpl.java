package com.agriva.finance.service.impl;

import com.agriva.finance.model.dto.request.ExpenseRequest;
import com.agriva.finance.model.dto.request.ExpenseSubmissionEvent;
import com.agriva.finance.model.dto.response.ExpenseBulkUploadResponse;
import com.agriva.finance.model.dto.response.ExpenseResponse;
import com.agriva.finance.model.dto.response.ExpenseStagingResponse;
import com.agriva.finance.model.dto.response.ExpenseSubmissionAck;
import com.agriva.shared.exception.DuplicateExpenseException;
import com.agriva.shared.exception.ExpenseNotFoundException;
import com.agriva.shared.exception.InvestmentLimitExceededException;
import com.agriva.shared.exception.NoActiveInvestmentException;
import com.agriva.finance.kafka.producer.ExpenseProducer;
import com.agriva.finance.mapper.ExpenseMapper;
import com.agriva.shared.models.dto.CursorPageResponse;
import com.agriva.finance.model.entity.Expense;
import com.agriva.finance.model.entity.Investment;
import com.agriva.finance.model.entity.StagedExpense;
import com.agriva.identity.models.entity.User;
import com.agriva.finance.model.enums.StagedExpenseStatus;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.finance.repository.ExpenseRepository;
import com.agriva.finance.repository.InvestmentRepository;
import com.agriva.finance.repository.StagedExpenseRepository;
import com.agriva.finance.service.ExpenseService;
import com.agriva.shared.utils.CursorCodec;
import com.agriva.finance.utils.ExpenseExcelParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
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
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepo;
    private final StagedExpenseRepository stagedRepo;
    private final InvestmentRepository investmentRepo;
    private final AuthRepository authRepository;
    private final ExpenseProducer expenseProducer;
    private final ExpenseExcelParser excelParser;
    private final ExpenseMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    public ExpenseSubmissionAck submitExpense(ExpenseRequest request, UUID farmId,
                                              UUID userId, String email) {
        UUID submissionId = UUID.randomUUID();
        ExpenseSubmissionEvent event = new ExpenseSubmissionEvent(
                submissionId, farmId, userId, email, serialize(request), LocalDateTime.now());
        expenseProducer.publish(event);
        log.info("Expense submission {} queued for farm {}", submissionId, farmId);
        return new ExpenseSubmissionAck(submissionId, farmId, "QUEUED", LocalDateTime.now());
    }

    @Override
    public ExpenseBulkUploadResponse bulkUpload(MultipartFile file, UUID farmId,
                                                UUID userId, String email) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required");
        }

        List<ExpenseExcelParser.ParsedRow> rows;
        try {
            rows = excelParser.parse(file.getInputStream());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to read Excel file: " + e.getMessage(), e);
        }

        ExpenseBulkUploadResponse response = new ExpenseBulkUploadResponse();
        response.setTotalRows(rows.size());

        for (ExpenseExcelParser.ParsedRow row : rows) {
            if (row.isError()) {
                response.setFailed(response.getFailed() + 1);
                response.getResults().add(new ExpenseBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", row.error()));
                continue;
            }

            UUID submissionId = UUID.randomUUID();
            expenseProducer.publish(new ExpenseSubmissionEvent(
                    submissionId, farmId, userId, email,
                    serialize(row.request()), LocalDateTime.now()));

            response.setQueued(response.getQueued() + 1);
            response.getResults().add(new ExpenseBulkUploadResponse.RowResult(
                    row.rowNumber(), submissionId, "QUEUED", null));
        }

        log.info("Bulk expense upload for farm {}: total={}, queued={}, failed={}",
                farmId, response.getTotalRows(), response.getQueued(), response.getFailed());
        return response;
    }

    @Override
    @Transactional
    public ExpenseResponse createDirect(ExpenseRequest request, UUID farmId, UUID userId) {
        Investment active = investmentRepo.findActiveForUpdate(farmId)
                .orElseThrow(() -> new NoActiveInvestmentException(
                        "No active investment for this farm. Create and activate one first."));

        BigDecimal spent = investmentRepo.sumExpensesByInvestmentIdOrZero(active.getId());
        if (spent.add(request.getAmountPaid()).compareTo(active.getAmount()) > 0) {
            throw new InvestmentLimitExceededException(String.format(
                    "Investment limit exceeded. Remaining: %s %s.",
                    active.getAmount().subtract(spent).toPlainString(),
                    active.getCurrency().name()));
        }

        Expense expense = Expense.builder()
                .farmId(farmId)
                .transactionId(request.getTransactionId())
                .investmentId(active.getId())
                .reason(request.getReason())
                .qtyPurchased(request.getQtyPurchased())
                .amountPaid(request.getAmountPaid())
                .expectedAmount(request.getExpectedAmount())
                .receipt(request.getReceipt())
                .currency(request.getCurrency())
                .category(request.getCategory())
                .expenseDate(request.getExpenseDate())
                .createdByUserId(userId)
                .build();

        expense.calculateDueBalance();
        Expense saved = expenseRepo.save(expense);
        log.info("Expense {} created directly for farm {} (investment {})",
                saved.getId(), farmId, active.getId());
        return mapper.toResponse(saved, lookupUsers(List.of(saved)));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ExpenseResponse> listExpenses(UUID farmId, Pageable pageable) {
        Page<Expense> page = expenseRepo.findByFarmIdLive(farmId, pageable);
        Map<UUID, User> userById = lookupUsers(page.getContent());
        return page.map(e -> mapper.toResponse(e, userById));
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseResponse getExpense(UUID id, UUID farmId) {
        Expense expense = expenseRepo.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found with ID: " + id));
        return mapper.toResponse(expense, lookupUsers(List.of(expense)));
    }

    @Override
    @Transactional
    public ExpenseResponse updateExpense(UUID id, ExpenseRequest request, UUID farmId) {
        Expense expense = expenseRepo.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found with ID: " + id));

        // Duplicate guard
        if (expenseRepo.existsByFarmIdAndAmountPaidAndExpenseDateAndReasonLive(
                farmId, request.getAmountPaid(), request.getExpenseDate(), request.getReason())
                && !(expense.getAmountPaid().compareTo(request.getAmountPaid()) == 0
                && expense.getExpenseDate().equals(request.getExpenseDate())
                && Objects.equals(expense.getReason(), request.getReason()))) {
            throw new DuplicateExpenseException(
                    "Another expense with the same amount, date and reason already exists");
        }

        // Budget guard
        if (expense.getInvestmentId() != null) {
            Investment investment = investmentRepo.findById(expense.getInvestmentId()).orElse(null);
            if (investment != null) {
                BigDecimal spentExcludingThis = investmentRepo
                        .sumExpensesByInvestmentIdExcludingOrZero(
                                investment.getId(), expense.getId());
                BigDecimal projected = spentExcludingThis.add(request.getAmountPaid());
                if (projected.compareTo(investment.getAmount()) > 0) {
                    BigDecimal remaining = investment.getAmount().subtract(spentExcludingThis);
                    throw new InvestmentLimitExceededException(String.format(
                            "Investment limit exceeded. Remaining balance: %s %s.",
                            remaining.toPlainString(), investment.getCurrency()));
                }
            }
        }

        expense.setReason(request.getReason());
        expense.setQtyPurchased(request.getQtyPurchased());
        expense.setAmountPaid(request.getAmountPaid());
        expense.setExpectedAmount(request.getExpectedAmount());
        expense.setReceipt(request.getReceipt());
        expense.setCurrency(request.getCurrency());
        expense.setCategory(request.getCategory());
        expense.setExpenseDate(request.getExpenseDate());
        expense.setTransactionId(request.getTransactionId());
        expense.calculateDueBalance();

        Expense saved = expenseRepo.save(expense);
        log.info("Expense {} updated for farm {}", id, farmId);
        return mapper.toResponse(saved, lookupUsers(List.of(saved)));
    }

    @Override
    @Transactional
    public void deleteExpense(UUID id, UUID farmId) {
        Expense expense = expenseRepo.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new ExpenseNotFoundException("Expense not found with ID: " + id));
        expense.setDeletedAt(LocalDateTime.now());
        expenseRepo.save(expense);
        log.info("Soft-deleted expense {} from farm {}", id, farmId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ExpenseStagingResponse> listStaging(UUID farmId, String status, Pageable pageable) {
        Page<StagedExpense> page;
        if (status != null && !status.isBlank()) {
            page = stagedRepo.findByFarmIdAndStatus(
                    farmId, StagedExpenseStatus.valueOf(status.toUpperCase()), pageable);
        } else {
            page = stagedRepo.findByFarmId(farmId, pageable);
        }
        return page.map(mapper::toStaging);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> stagingStats(UUID farmId) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (StagedExpenseStatus s : StagedExpenseStatus.values()) {
            out.put(s.name(), stagedRepo.countByFarmIdAndStatus(farmId, s));
        }
        return out;
    }

    private String serialize(Object o) {
        try { return objectMapper.writeValueAsString(o); }
        catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize expense payload", e);
        }
    }

    private Map<UUID, User> lookupUsers(List<Expense> expenses) {
        Set<UUID> userIds = expenses.stream()
                .map(Expense::getCreatedByUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) return Map.of();
        return authRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<ExpenseResponse> listExpensesByCursor(UUID farmId, String cursor, int size) {
        int cappedSize = Math.clamp(size, 1, 100);

        org.springframework.data.domain.Pageable limit =
                PageRequest.of(0, cappedSize + 1);

        java.time.LocalDate cursorDate = null;
        java.util.UUID cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            CursorCodec.Decoded d = CursorCodec.decodeDate(cursor);
            cursorDate = (java.time.LocalDate) d.sortValue();
            cursorId = d.id();
        }

        List<Expense> rows = expenseRepo.findCursorPage(farmId, cursorDate, cursorId, limit);

        boolean hasNext = rows.size() > cappedSize;
        List<Expense> page = hasNext ? rows.subList(0, cappedSize) : rows;

        Map<UUID, User> userById = lookupUsers(page);
        List<ExpenseResponse> dtos = page.stream()
                .map(e -> mapper.toResponse(e, userById))
                .toList();

        String nextCursor = hasNext
                ? CursorCodec.encode(page.get(page.size() - 1).getExpenseDate(),
                page.get(page.size() - 1).getId())
                : null;

        return new CursorPageResponse<>(dtos, nextCursor, hasNext, dtos.size());
    }
}