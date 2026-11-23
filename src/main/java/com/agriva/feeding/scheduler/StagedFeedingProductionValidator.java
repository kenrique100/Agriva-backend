package com.agriva.feeding.scheduler;

import com.agriva.feeding.model.dto.request.ProductionRequest;
import com.agriva.feeding.model.dto.request.ProductionSubmissionPayload;
import com.agriva.feeding.model.dto.response.FeedBatchResponse;
import com.agriva.feeding.model.entity.StagedFeedingProduction;
import com.agriva.feeding.repository.FormulationRepository;
import com.agriva.feeding.repository.StagedFeedingProductionRepository;
import com.agriva.feeding.service.ProductionService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.ResourceNotFoundException;
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
public class StagedFeedingProductionValidator {

    private static final int ERROR_FIELD_LIMIT = 1990;
    private static final String LOCK_KEY = "staged-feeding-production-validator";
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(55);

    private final StagedFeedingProductionRepository stagedRepo;
    private final FormulationRepository formulationRepo;
    private final ProductionService productionService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;
    private final DistributedLockService lock;

    @Value("${agriva.feeding.production.validator.batch-size:100}")
    private int batchSize;

    @Value("${agriva.feeding.production.validator.max-attempts:4}")
    private int maxAttempts;

    @Value("${agriva.feeding.production.validator.retry-delay-minutes:1}")
    private long retryDelayMinutes;

    @Value("${agriva.feeding.validator.per-farm-lock.enabled:false}")
    private boolean perFarmLockEnabled;

    @Scheduled(cron = "${agriva.feeding.production.validator.cron:0 * * * * *}")
    public void processStaged() {
        if (perFarmLockEnabled) {
            processStagedPerFarm();
        } else {
            processStagedGlobal();
        }
    }

    private void processStagedGlobal() {
        lock.runIfLocked(LOCK_KEY, LOCK_TIMEOUT, () -> {
            List<StagedFeedingProduction> due = stagedRepo.findDueForProcessing(
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
                List<StagedFeedingProduction> due = stagedRepo.findDueForProcessing(
                        farmId, LocalDateTime.now(),
                        StagedStatus.PENDING, StagedStatus.RETRYING,
                        PageRequest.of(0, batchSize));
                runBatch(due);
                return null;
            });
        }
    }

    private void runBatch(List<StagedFeedingProduction> due) {
        if (due.isEmpty()) return;
        log.info("StagedFeedingProductionValidator: {} row(s) due", due.size());
        for (StagedFeedingProduction s : due) {
            processRow(s.getId());
        }
    }

    public void processOneImmediately(UUID id) { processRow(id); }

    private void processRow(UUID id) {
        try {
            txTemplate.execute(status -> { tryProcessOne(id); return null; });
        } catch (Exception ex) {
            log.warn("Staged feeding-production {} failed (transient): {}", id, ex.getMessage());
            try {
                txTemplate.execute(status -> { recordFailure(id, ex.getMessage()); return null; });
            } catch (Exception recordEx) {
                log.error("Failed to record failure for {}: {}", id, recordEx.getMessage(), recordEx);
            }
        }
    }

    private void tryProcessOne(UUID id) {
        StagedFeedingProduction staged = stagedRepo.findByIdForUpdate(id).orElse(null);
        if (staged == null || !isDue(staged, LocalDateTime.now())) return;

        staged.setLastAttemptAt(LocalDateTime.now());

        try {
            ProductionSubmissionPayload payload = objectMapper.readValue(
                    staged.getPayloadJson(), ProductionSubmissionPayload.class);

            if (payload.producedKg() == null || payload.producedKg().signum() <= 0) {
                throw new BusinessException("producedKg must be positive");
            }
            if (payload.productionDate() == null
                    || payload.productionDate().isAfter(java.time.LocalDate.now())) {
                throw new BusinessException("productionDate cannot be in the future");
            }

            var formulation = formulationRepo.findMostRecentByFarmIdAndNameLive(
                            staged.getFarmId(), payload.formulationName())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Formulation not found: " + payload.formulationName()));

            ProductionRequest req = new ProductionRequest();
            req.setFormulationId(formulation.getId());
            req.setProducedKg(payload.producedKg());
            req.setProductionDate(payload.productionDate());
            req.setNotes(payload.notes());
            req.setForecastId(payload.forecastId());

            FeedBatchResponse created = productionService.produce(
                    req, staged.getFarmId(), staged.getSubmittedByUserId());

            staged.setStatus(StagedStatus.MOVED);
            staged.setMovedBatchId(created.getId());
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            staged.setValidationErrors(null);
            stagedRepo.save(staged);

            log.info("Staged feeding-production {} MOVED -> batch {}",
                    staged.getId(), created.getId());

        } catch (JsonProcessingException e) {
            markTerminal(staged, "Invalid JSON payload: " + e.getMessage());
        } catch (ResourceNotFoundException | BusinessException e) {
            markTerminal(staged, e.getMessage());
        }
    }

    private void markTerminal(StagedFeedingProduction staged, String msg) {
        int current = staged.getRetryCount() == null ? 0 : staged.getRetryCount();
        int next = Math.min(current + 1, maxAttempts);
        staged.setRetryCount(next);
        staged.setValidationErrors(truncate(msg));
        staged.setStatus(StagedStatus.FAILED);
        staged.setProcessedAt(LocalDateTime.now());
        staged.setNextRetryAt(null);
        stagedRepo.save(staged);
        log.warn("Staged feeding-production {} FAILED (terminal): {}", staged.getId(), msg);
    }

    private void recordFailure(UUID id, String message) {
        StagedFeedingProduction staged = stagedRepo.findByIdForUpdate(id).orElse(null);
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
            log.error("Staged feeding-production {} FAILED after {} attempts: {}",
                    staged.getId(), attempt, message);
        } else {
            staged.setStatus(StagedStatus.RETRYING);
            staged.setNextRetryAt(LocalDateTime.now().plusMinutes(retryDelayMinutes));
            log.warn("Staged feeding-production {} RETRY {}/{} — {}",
                    staged.getId(), attempt, maxAttempts, message);
        }
        stagedRepo.save(staged);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= ERROR_FIELD_LIMIT ? s : s.substring(0, ERROR_FIELD_LIMIT);
    }

    private static boolean isDue(StagedFeedingProduction s, LocalDateTime now) {
        if (s.getStatus() == StagedStatus.PENDING) return true;
        if (s.getStatus() != StagedStatus.RETRYING) return false;
        return s.getNextRetryAt() == null || !s.getNextRetryAt().isAfter(now);
    }
}