package com.agriva.feeding.scheduler;

import com.agriva.feeding.model.dto.request.IngredientRequest;
import com.agriva.feeding.model.dto.request.IngredientSubmissionPayload;
import com.agriva.feeding.model.dto.response.IngredientResponse;
import com.agriva.feeding.model.entity.StagedFeedingIngredient;
import com.agriva.feeding.repository.StagedFeedingIngredientRepository;
import com.agriva.feeding.service.IngredientService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.lock.DistributedLockService;
import com.agriva.shared.models.enums.StagedStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
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
public class StagedFeedingIngredientValidator {

    private static final int ERROR_FIELD_LIMIT = 1990;
    private static final String LOCK_KEY = "staged-feeding-ingredient-validator";
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(55);

    private final StagedFeedingIngredientRepository stagedRepo;
    private final IngredientService ingredientService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;
    private final DistributedLockService lock;

    @Value("${agriva.feeding.ingredient.validator.batch-size:100}")
    private int batchSize;

    @Value("${agriva.feeding.ingredient.validator.max-attempts:4}")
    private int maxAttempts;

    @Value("${agriva.feeding.ingredient.validator.retry-delay-minutes:1}")
    private long retryDelayMinutes;

    @Value("${agriva.feeding.validator.per-farm-lock.enabled:false}")
    private boolean perFarmLockEnabled;

    @Scheduled(cron = "${agriva.feeding.ingredient.validator.cron:0 * * * * *}")
    public void processStaged() {
        if (perFarmLockEnabled) {
            processStagedPerFarm();
        } else {
            processStagedGlobal();
        }
    }

    private void processStagedGlobal() {
        lock.runIfLocked(LOCK_KEY, LOCK_TIMEOUT, () -> {
            List<StagedFeedingIngredient> due = stagedRepo.findDueForProcessing(
                    LocalDateTime.now(), StagedStatus.PENDING, StagedStatus.RETRYING,
                    PageRequest.of(0, batchSize));
            runBatch(due);
            return null;
        });
    }

    private void processStagedPerFarm() {
        LocalDateTime now = LocalDateTime.now();
        List<UUID> farmIds = stagedRepo.findDistinctFarmIdsWithDueRows(
                now, StagedStatus.PENDING, StagedStatus.RETRYING);
        for (UUID farmId : farmIds) {
            String farmLockKey = LOCK_KEY + ":" + farmId;
            lock.runIfLocked(farmLockKey, LOCK_TIMEOUT, () -> {
                List<StagedFeedingIngredient> due = stagedRepo.findDueForProcessing(
                        farmId, LocalDateTime.now(),
                        StagedStatus.PENDING, StagedStatus.RETRYING,
                        PageRequest.of(0, batchSize));
                runBatch(due);
                return null;
            });
        }
    }

    private void runBatch(List<StagedFeedingIngredient> due) {
        if (due.isEmpty()) return;
        log.info("StagedFeedingIngredientValidator: {} row(s) due", due.size());
        for (StagedFeedingIngredient s : due) {
            processRow(s.getId());
        }
    }

    public void processOneImmediately(UUID id) { processRow(id); }

    private void processRow(UUID id) {
        try {
            txTemplate.execute(status -> { tryProcessOne(id); return null; });
        } catch (Exception ex) {
            log.warn("Staged feeding-ingredient {} failed (transient): {}", id, ex.getMessage());
            try {
                txTemplate.execute(status -> { recordFailure(id, ex.getMessage()); return null; });
            } catch (Exception recordEx) {
                log.error("Failed to record failure for {}: {}", id, recordEx.getMessage(), recordEx);
            }
        }
    }

    private void tryProcessOne(UUID id) {
        StagedFeedingIngredient staged = stagedRepo.findByIdForUpdate(id).orElse(null);
        if (staged == null || !isDue(staged, LocalDateTime.now())) return;

        staged.setLastAttemptAt(LocalDateTime.now());

        try {
            IngredientSubmissionPayload payload = objectMapper.readValue(
                    staged.getPayloadJson(), IngredientSubmissionPayload.class);

            if (payload.name() == null || payload.name().isBlank()) {
                throw new BusinessException("name is required");
            }
            if (payload.unit() == null) {
                throw new BusinessException("unit is required");
            }
            if (payload.quantity() != null && payload.quantity().signum() < 0) {
                throw new BusinessException("quantity cannot be negative");
            }

            IngredientRequest req = new IngredientRequest();
            req.setName(payload.name());
            req.setUnit(payload.unit());
            req.setCostPerUnit(payload.costPerUnit());
            req.setCurrency(payload.currency());
            req.setSupplier(payload.supplier());
            req.setDensityKgPerL(payload.densityKgPerL());
            req.setCrudeProteinPct(payload.crudeProteinPct());

            IngredientResponse created = ingredientService.createWithInitialStock(
                    req, payload.quantity(), staged.getFarmId(), staged.getSubmittedByUserId());

            staged.setStatus(StagedStatus.MOVED);
            staged.setMovedIngredientId(created.getId());
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            staged.setValidationErrors(null);
            stagedRepo.save(staged);

            log.info("Staged feeding-ingredient {} MOVED -> ingredient {}",
                    staged.getId(), created.getId());

        } catch (JsonProcessingException e) {
            markTerminal(staged, "Invalid JSON payload: " + e.getMessage());
        } catch (BusinessException e) {
            markTerminal(staged, e.getMessage());
        }
    }

    private void markTerminal(StagedFeedingIngredient staged, String msg) {
        // Stage 2 / R4 + R5: unify with the other three validators (was missing).
        int current = staged.getRetryCount() == null ? 0 : staged.getRetryCount();
        int next = Math.min(current + 1, maxAttempts);
        staged.setRetryCount(next);
        staged.setValidationErrors(truncate(msg));
        staged.setStatus(StagedStatus.FAILED);
        staged.setProcessedAt(LocalDateTime.now());
        staged.setNextRetryAt(null);
        stagedRepo.save(staged);
        log.warn("Staged feeding-ingredient {} FAILED (terminal): {}", staged.getId(), msg);
    }

    private void recordFailure(UUID id, String message) {
        StagedFeedingIngredient staged = stagedRepo.findByIdForUpdate(id).orElse(null);
        if (staged == null) return;

        staged.setLastAttemptAt(LocalDateTime.now());

        int current = staged.getRetryCount() == null ? 0 : staged.getRetryCount();
        int attempt = Math.min(current + 1, maxAttempts);
        staged.setRetryCount(attempt);
        staged.setValidationErrors(truncate(message));

        if (attempt >= maxAttempts) {
            staged.setStatus(StagedStatus.FAILED);
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            log.error("Staged feeding-ingredient {} FAILED after {} attempt(s): {}",
                    staged.getId(), attempt, message);
        } else {
            staged.setStatus(StagedStatus.RETRYING);
            staged.setNextRetryAt(LocalDateTime.now().plusMinutes(retryDelayMinutes));
            log.warn("Staged feeding-ingredient {} RETRY {}/{} — {}",
                    staged.getId(), attempt, maxAttempts, message);
        }
        stagedRepo.save(staged);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= ERROR_FIELD_LIMIT ? s : s.substring(0, ERROR_FIELD_LIMIT);
    }

    private static boolean isDue(StagedFeedingIngredient s, LocalDateTime now) {
        if (s.getStatus() == StagedStatus.PENDING) return true;
        if (s.getStatus() != StagedStatus.RETRYING) return false;
        return s.getNextRetryAt() == null || !s.getNextRetryAt().isAfter(now);
    }
}