package com.agriva.scheduler;

import com.agriva.exception.InvestmentLimitExceededException;
import com.agriva.exception.NoActiveInvestmentException;
import com.agriva.model.dto.salary.SalaryPaymentRequest;
import com.agriva.model.dto.salary.SalaryPaymentResponse;
import com.agriva.model.entity.*;
import com.agriva.model.entity.enums.StagedSalaryStatus;
import com.agriva.repository.*;
import com.agriva.service.SalaryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StagedSalaryValidator {

    private static final int ERROR_FIELD_LIMIT = 1990;

    private final StagedSalaryRepository stagedRepo;
    private final PersonnelRepository personnelRepo;
    private final SalaryService salaryService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${agriva.salary.validator.batch-size:100}")
    private int batchSize;
    @Value("${agriva.salary.validator.max-attempts:4}")
    private int maxAttempts;
    @Value("${agriva.salary.validator.retry-delay-minutes:1}")
    private long retryDelayMinutes;

    @Scheduled(cron = "${agriva.salary.validator.cron:0 * * * * *}")
    public void processStagedSalaries() {
        List<StagedSalary> due = stagedRepo.findDueForProcessing(
                LocalDateTime.now(),
                StagedSalaryStatus.PENDING,
                StagedSalaryStatus.RETRYING,
                PageRequest.of(0, batchSize));
        if (due.isEmpty()) return;
        log.info("StagedSalaryValidator: {} row(s) due", due.size());
        for (StagedSalary staged : due) {
            transactionTemplate.execute(status -> { processOne(staged.getId()); return null; });
        }
    }

    private void processOne(UUID stagedId) {
        LocalDateTime now = LocalDateTime.now();
        StagedSalary staged = stagedRepo.findDueByIdForUpdate(
                stagedId, now, StagedSalaryStatus.PENDING, StagedSalaryStatus.RETRYING).orElse(null);
        if (staged == null) return;

        staged.setLastAttemptAt(now);
        try {
            SalaryPaymentRequest req = objectMapper.readValue(
                    staged.getPayloadJson(), SalaryPaymentRequest.class);

            String error = validate(req, staged.getFarmId());
            if (error != null) { onFailure(staged, List.of(error)); return; }

            SalaryPaymentResponse resp = salaryService.create(
                    req, staged.getFarmId(), staged.getSubmittedByUserId());

            staged.setStatus(StagedSalaryStatus.MOVED);
            staged.setMovedSalaryId(resp.getId());
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            staged.setValidationErrors(null);
            stagedRepo.save(staged);
            log.info("Staged salary {} MOVED -> salary {}", staged.getId(), resp.getId());
        } catch (NoActiveInvestmentException | InvestmentLimitExceededException e) {
            markTerminal(staged, e.getMessage());
        } catch (Exception e) {
            log.error("Staged salary {} threw: {}", staged.getId(), e.getMessage(), e);
            onFailure(staged, List.of("Processing error: " + e.getMessage()));
        }
    }

    private String validate(SalaryPaymentRequest req, UUID farmId) {
        if (req.getPersonnelId() == null) return "Personnel is required";
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0)
            return "Amount must be positive";
        if (req.getPaymentDate() == null) return "Payment date is required";
        if (req.getPaymentDate().isAfter(LocalDate.now()))
            return "Payment date cannot be in the future";
        // Use the Live finder — soft-deleted personnel are treated as "not found".
        if (personnelRepo.findByIdAndFarmIdLive(req.getPersonnelId(), farmId).isEmpty())
            return "Personnel not found in farm";
        return null;
    }

    private void markTerminal(StagedSalary staged, String message) {
        staged.setRetryCount((staged.getRetryCount() == null ? 0 : staged.getRetryCount()) + 1);
        staged.setValidationErrors(truncate(message));
        staged.setStatus(StagedSalaryStatus.FAILED);
        staged.setProcessedAt(LocalDateTime.now());
        staged.setNextRetryAt(null);
        stagedRepo.save(staged);
    }

    private void onFailure(StagedSalary staged, List<String> errors) {
        int attempt = (staged.getRetryCount() == null ? 0 : staged.getRetryCount()) + 1;
        staged.setRetryCount(attempt);
        staged.setValidationErrors(truncate(String.join("; ", errors)));
        if (attempt >= maxAttempts) {
            staged.setStatus(StagedSalaryStatus.FAILED);
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
        } else {
            staged.setStatus(StagedSalaryStatus.RETRYING);
            staged.setNextRetryAt(LocalDateTime.now().plusMinutes(retryDelayMinutes));
        }
        stagedRepo.save(staged);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= ERROR_FIELD_LIMIT ? s : s.substring(0, ERROR_FIELD_LIMIT);
    }
}