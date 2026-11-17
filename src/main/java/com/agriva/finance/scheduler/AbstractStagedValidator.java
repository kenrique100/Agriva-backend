package com.agriva.finance.scheduler;

import com.agriva.finance.model.entity.StagedSubmission;
import com.agriva.finance.model.enums.StagedStatus;
import com.agriva.shared.lock.DistributedLockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
public abstract class AbstractStagedValidator<S extends StagedSubmission, R> {

    private static final int ERROR_FIELD_LIMIT = 1990;
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(55);

    protected final ObjectMapper objectMapper;
    protected final TransactionTemplate transactionTemplate;
    protected final DistributedLockService lock;

    protected AbstractStagedValidator(ObjectMapper objectMapper,
                                      TransactionTemplate transactionTemplate,
                                      DistributedLockService lock) {
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.lock = lock;
    }

    protected abstract int getBatchSize();
    protected abstract int getMaxAttempts();
    protected abstract long getRetryDelayMinutes();
    protected abstract String typeName();
    protected abstract String getLockKey();
    protected abstract Class<R> requestType();
    protected abstract List<String> validateRequest(R request, UUID farmId);
    protected abstract List<S> fetchDueRows(int batchSize);
    protected abstract S findForUpdate(UUID stagedId);
    protected abstract void saveStaged(S staged);
    protected abstract PromotionResult promote(S staged, R request);


    public record PromotionResult(UUID movedId, String terminalError) {
        public static PromotionResult moved(UUID id)          { return new PromotionResult(id, null); }
        public static PromotionResult terminal(String error)  { return new PromotionResult(null, error); }
    }

    public void process() {
        lock.runIfLocked(getLockKey(), LOCK_TIMEOUT, () -> {
            List<S> due = fetchDueRows(getBatchSize());
            if (due.isEmpty()) return null;

            log.info("{}Validator: {} row(s) due for processing", typeName(), due.size());

            for (S staged : due) {
                UUID stagedId = staged.getId();
                transactionTemplate.execute(status -> {
                    processOne(stagedId);
                    return null;
                });
            }
            return null;
        });
    }

    protected void processOne(UUID stagedId) {
        S staged = findForUpdate(stagedId);
        if (staged == null) return;

        LocalDateTime now = LocalDateTime.now();
        if (!isDueForProcessing(staged, now)) return;

        staged.setLastAttemptAt(now);
        try {
            R request = objectMapper.readValue(staged.getPayloadJson(), requestType());

            List<String> errors = validateRequest(request, staged.getFarmId());
            if (!errors.isEmpty()) {
                onFailure(staged, errors);
                return;
            }

            PromotionResult result = promote(staged, request);
            if (result.terminalError() != null) {
                staged.setValidationErrors(truncate(result.terminalError()));
                markFailed(staged, LocalDateTime.now());
                saveStaged(staged);
                log.warn("Staged {} {} FAILED (terminal): {}",
                        typeName(), staged.getId(), result.terminalError());
                return;
            }

            staged.setValidationErrors(null);
            markMoved(staged, result.movedId(), LocalDateTime.now());
            saveStaged(staged);
            log.info("Staged {} {} MOVED -> {}",
                    typeName(), staged.getId(), result.movedId());

        } catch (Exception e) {
            log.error("Staged {} {} threw during processing: {}",
                    typeName(), staged.getId(), e.getMessage(), e);
            onFailure(staged, List.of("Processing error: " + e.getMessage()));
        }
    }

    protected void onFailure(S staged, List<String> errors) {
        int attempt = (staged.getRetryCount() == null ? 0 : staged.getRetryCount()) + 1;
        staged.setRetryCount(attempt);
        staged.setValidationErrors(truncate(String.join("; ", errors)));

        if (attempt >= getMaxAttempts()) {
            markFailed(staged, LocalDateTime.now());
            saveStaged(staged);
            log.error("Staged {} {} FAILED after {} attempt(s): {}",
                    typeName(), staged.getId(), attempt, errors);
        } else {
            LocalDateTime nextRetry = LocalDateTime.now().plusMinutes(getRetryDelayMinutes());
            markRetrying(staged, nextRetry);
            saveStaged(staged);
            log.warn("Staged {} {} RETRY {}/{} scheduled for {}: {}",
                    typeName(), staged.getId(), attempt, getMaxAttempts(), nextRetry, errors);
        }
    }

    protected void markMoved(S staged, UUID movedId, LocalDateTime now) {
        staged.setStatus(StagedStatus.MOVED);
        setMovedReference(staged, movedId);
        staged.setProcessedAt(now);
        staged.setNextRetryAt(null);
    }

    protected void markFailed(S staged, LocalDateTime now) {
        staged.setStatus(StagedStatus.FAILED);
        staged.setProcessedAt(now);
        staged.setNextRetryAt(null);
    }

    protected void markRetrying(S staged, LocalDateTime nextRetry) {
        staged.setStatus(StagedStatus.RETRYING);
        staged.setNextRetryAt(nextRetry);
    }

    protected abstract void setMovedReference(S staged, UUID movedId);

    protected static boolean isDueForProcessing(StagedSubmission staged, LocalDateTime now) {
        StagedStatus status = staged.getStatus();
        if (status == StagedStatus.PENDING)  return true;
        if (status != StagedStatus.RETRYING) return false;
        LocalDateTime nextRetry = staged.getNextRetryAt();
        return nextRetry == null || !nextRetry.isAfter(now);
    }

    protected static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= ERROR_FIELD_LIMIT ? s : s.substring(0, ERROR_FIELD_LIMIT);
    }
}