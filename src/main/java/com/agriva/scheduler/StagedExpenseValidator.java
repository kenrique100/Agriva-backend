package com.agriva.scheduler;

import com.agriva.model.dto.expense.ExpenseRequest;
import com.agriva.model.entity.Expense;
import com.agriva.model.entity.StagedExpense;
import com.agriva.model.entity.StagedExpenseStatus;
import com.agriva.repository.ExpenseRepository;
import com.agriva.repository.StagedExpenseRepository;
import com.agriva.utils.ExpenseValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StagedExpenseValidator {

    private static final int ERROR_FIELD_LIMIT = 1990;

    private final StagedExpenseRepository stagedRepo;
    private final ExpenseRepository expenseRepo;
    private final ExpenseValidator expenseValidator;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${agriva.expense.validator.batch-size:100}")
    private int batchSize;

    @Value("${agriva.expense.validator.max-attempts:4}")
    private int maxAttempts;

    @Value("${agriva.expense.validator.retry-delay-minutes:1}")
    private long retryDelayMinutes;

    @Scheduled(cron = "${agriva.expense.validator.cron:0 * * * * *}")
    public void processStagedExpenses() {
        List<StagedExpense> due =
                stagedRepo.findDueForProcessing(LocalDateTime.now(),
                        PageRequest.of(0, batchSize));

        if (due.isEmpty()) return;

        log.info("StagedExpenseValidator: {} row(s) due for processing", due.size());

        for (StagedExpense staged : due) {
            transactionTemplate.execute(status -> {
                processOne(staged);
                return null;
            });
        }
    }

    private void processOne(StagedExpense staged) {
        staged.setLastAttemptAt(LocalDateTime.now());

        try {
            ExpenseRequest request =
                    objectMapper.readValue(staged.getPayloadJson(), ExpenseRequest.class);

            List<String> errors = expenseValidator.validate(request, staged.getFarmId());

            if (!errors.isEmpty()) {
                onFailure(staged, errors);
                return;
            }

            Expense expense = Expense.builder()
                    .farmId(staged.getFarmId())
                    .transactionId(request.getTransactionId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency() == null ? "USD" : request.getCurrency())
                    .category(request.getCategory())
                    .vendor(request.getVendor())
                    .description(request.getDescription())
                    .expenseDate(request.getExpenseDate())
                    .createdByUserId(staged.getSubmittedByUserId())
                    .build();

            Expense saved = expenseRepo.save(expense);

            staged.setStatus(StagedExpenseStatus.MOVED);
            staged.setMovedExpenseId(saved.getId());
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            staged.setValidationErrors(null);
            stagedRepo.save(staged);

            log.info("Staged expense {} MOVED -> expense {}", staged.getId(), saved.getId());

        } catch (Exception e) {
            log.error("Staged expense {} threw during processing: {}",
                    staged.getId(), e.getMessage(), e);
            onFailure(staged, List.of("Processing error: " + e.getMessage()));
        }
    }

    private void onFailure(StagedExpense staged, List<String> errors) {
        int attempt = (staged.getRetryCount() == null ? 0 : staged.getRetryCount()) + 1;
        staged.setRetryCount(attempt);
        staged.setValidationErrors(truncate(String.join("; ", errors)));

        if (attempt >= maxAttempts) {
            staged.setStatus(StagedExpenseStatus.FAILED);
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            stagedRepo.save(staged);
            log.error("Staged expense {} FAILED after {} attempt(s): {}",
                    staged.getId(), attempt, errors);
        } else {
            LocalDateTime nextRetry = LocalDateTime.now().plusMinutes(retryDelayMinutes);
            staged.setStatus(StagedExpenseStatus.RETRYING);
            staged.setNextRetryAt(nextRetry);
            stagedRepo.save(staged);
            log.warn("Staged expense {} RETRY {}/{} scheduled for {}: {}",
                    staged.getId(), attempt, maxAttempts, nextRetry, errors);
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= ERROR_FIELD_LIMIT ? s : s.substring(0, ERROR_FIELD_LIMIT);
    }
}