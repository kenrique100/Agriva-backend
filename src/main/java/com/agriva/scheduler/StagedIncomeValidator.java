package com.agriva.scheduler;

import com.agriva.model.dto.income.IncomeRequest;
import com.agriva.model.entity.Income;
import com.agriva.model.entity.StagedIncome;
import com.agriva.model.entity.StagedIncomeStatus;
import com.agriva.repository.IncomeRepository;
import com.agriva.repository.StagedIncomeRepository;
import com.agriva.utils.IncomeValidator;
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
public class StagedIncomeValidator {

    private static final int ERROR_FIELD_LIMIT = 1990;

    private final StagedIncomeRepository stagedRepo;
    private final IncomeRepository incomeRepo;
    private final IncomeValidator incomeValidator;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${agriva.income.validator.batch-size:100}")
    private int batchSize;

    @Value("${agriva.income.validator.max-attempts:4}")
    private int maxAttempts;

    @Value("${agriva.income.validator.retry-delay-minutes:1}")
    private long retryDelayMinutes;

    @Scheduled(cron = "${agriva.income.validator.cron:0 * * * * *}")
    public void processStagedIncomes() {
        List<StagedIncome> due =
                stagedRepo.findDueForProcessing(LocalDateTime.now(),
                        PageRequest.of(0, batchSize));

        if (due.isEmpty()) return;

        log.info("StagedIncomeValidator: {} row(s) due for processing", due.size());

        for (StagedIncome staged : due) {
            transactionTemplate.execute(status -> {
                processOne(staged);
                return null;
            });
        }
    }

    private void processOne(StagedIncome staged) {
        staged.setLastAttemptAt(LocalDateTime.now());

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
}