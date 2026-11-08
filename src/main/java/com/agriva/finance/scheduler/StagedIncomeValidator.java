package com.agriva.finance.scheduler;

import com.agriva.finance.model.dto.request.IncomeRequest;
import com.agriva.finance.model.entity.Income;
import com.agriva.finance.model.entity.StagedIncome;
import com.agriva.finance.model.enums.StagedIncomeStatus;
import com.agriva.finance.repository.IncomeRepository;
import com.agriva.finance.repository.StagedIncomeRepository;
import com.agriva.shared.lock.DistributedLockService;
import com.agriva.finance.utils.IncomeValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StagedIncomeValidator {

    private static final int ERROR_FIELD_LIMIT = 1990;

    private final StagedIncomeRepository stagedRepo;
    private final IncomeRepository incomeRepo;
    private final IncomeValidator incomeValidator;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final DistributedLockService lock;

    @Value("${agriva.income.validator.batch-size:100}")
    private int batchSize;

    @Value("${agriva.income.validator.max-attempts:4}")
    private int maxAttempts;

    @Value("${agriva.income.validator.retry-delay-minutes:1}")
    private long retryDelayMinutes;

    @Scheduled(cron = "${agriva.income.validator.cron:0 * * * * *}")
    public void processStagedIncomes() {
        lock.runIfLocked("staged-income-validator", Duration.ofSeconds(55), () -> {
            List<StagedIncome> due = stagedRepo.findDueForProcessing(
                    LocalDateTime.now(),
                    StagedIncomeStatus.PENDING,
                    StagedIncomeStatus.RETRYING,
                    PageRequest.of(0, batchSize));

            if (due.isEmpty()) return null;

            log.info("StagedIncomeValidator: {} row(s) due for processing", due.size());

            for (StagedIncome staged : due) {
                UUID stagedId = staged.getId();
                transactionTemplate.execute(status -> {
                    processOne(stagedId);
                    return null;
                });
            }
            return null;
        });
    }

    private void processOne(UUID stagedId) {
        // FIX: renamed `locked` to `staged` so the rest of the method compiles cleanly
        StagedIncome staged = stagedRepo.findByIdForUpdate(stagedId).orElse(null);
        if (staged == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!isDueForProcessing(staged, now)) {
            return;
        }

        staged.setLastAttemptAt(now);
        try {
            IncomeRequest request =
                    objectMapper.readValue(staged.getPayloadJson(), IncomeRequest.class);

            List<String> errors = incomeValidator.validate(request, staged.getFarmId());
            if (!errors.isEmpty()) {
                onFailure(staged, errors);
                return;
            }

            Income income = Income.builder()
                    .farmId(staged.getFarmId())
                    .transactionId(request.getTransactionId())
                    .reason(request.getReason())
                    .quantity(request.getQuantity())
                    .amountReceived(request.getAmountReceived())
                    .expectedAmount(request.getExpectedAmount())
                    .receipt(request.getReceipt())
                    .currency(request.getCurrency())
                    .category(request.getCategory())
                    .incomeDate(request.getIncomeDate())
                    .createdByUserId(staged.getSubmittedByUserId())
                    .build();

            income.calculateDueBalance();
            Income saved = incomeRepo.save(income);

            staged.setStatus(StagedIncomeStatus.MOVED);
            staged.setMovedIncomeId(saved.getId());
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            staged.setValidationErrors(null);
            stagedRepo.save(staged);

            log.info("Staged income {} MOVED -> income {}", staged.getId(), saved.getId());

        } catch (Exception e) {
            log.error("Staged income {} threw during processing: {}",
                    staged.getId(), e.getMessage(), e);
            onFailure(staged, List.of("Processing error: " + e.getMessage()));
        }
    }

    private void onFailure(StagedIncome staged, List<String> errors) {
        int attempt = (staged.getRetryCount() == null ? 0 : staged.getRetryCount()) + 1;
        staged.setRetryCount(attempt);
        staged.setValidationErrors(truncate(String.join("; ", errors)));

        if (attempt >= maxAttempts) {
            staged.setStatus(StagedIncomeStatus.FAILED);
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            stagedRepo.save(staged);
            log.error("Staged income {} FAILED after {} attempt(s): {}",
                    staged.getId(), attempt, errors);
        } else {
            LocalDateTime nextRetry = LocalDateTime.now().plusMinutes(retryDelayMinutes);
            staged.setStatus(StagedIncomeStatus.RETRYING);
            staged.setNextRetryAt(nextRetry);
            stagedRepo.save(staged);
            log.warn("Staged income {} RETRY {}/{} scheduled for {}: {}",
                    staged.getId(), attempt, maxAttempts, nextRetry, errors);
        }
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= ERROR_FIELD_LIMIT ? s : s.substring(0, ERROR_FIELD_LIMIT);
    }

    private static boolean isDueForProcessing(StagedIncome staged, LocalDateTime now) {
        if (staged.getStatus() == StagedIncomeStatus.PENDING) {
            return true;
        }

        if (staged.getStatus() != StagedIncomeStatus.RETRYING) {
            return false;
        }

        return staged.getNextRetryAt() == null || !staged.getNextRetryAt().isAfter(now);
    }
}