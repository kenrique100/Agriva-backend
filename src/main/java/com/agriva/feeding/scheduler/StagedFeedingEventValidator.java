package com.agriva.feeding.scheduler;

import com.agriva.feeding.model.dto.request.FeedingRequest;
import com.agriva.feeding.model.dto.request.FeedingSubmissionPayload;
import com.agriva.feeding.model.dto.response.FeedingEventResponse;
import com.agriva.feeding.model.entity.StagedFeedingEvent;
import com.agriva.feeding.port.FeedConsumerPort;
import com.agriva.feeding.repository.StagedFeedingEventRepository;
import com.agriva.feeding.service.FeedConsumptionService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.ConsumerNotFoundInDomainException;
import com.agriva.shared.exception.UnregisteredConsumerTypeException;
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
public class StagedFeedingEventValidator {

    private static final int ERROR_FIELD_LIMIT = 1990;
    private static final String LOCK_KEY = "staged-feeding-event-validator";
    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(55);

    private final StagedFeedingEventRepository stagedRepo;
    private final List<FeedConsumerPort> consumerPorts;
    private final FeedConsumptionService consumptionService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;
    private final DistributedLockService lock;

    @Value("${agriva.feeding.feeding.validator.batch-size:100}")
    private int batchSize;

    @Value("${agriva.feeding.feeding.validator.max-attempts:4}")
    private int maxAttempts;

    @Value("${agriva.feeding.feeding.validator.retry-delay-minutes:1}")
    private long retryDelayMinutes;

    @Value("${agriva.feeding.validator.per-farm-lock.enabled:false}")
    private boolean perFarmLockEnabled;

    @Scheduled(cron = "${agriva.feeding.feeding.validator.cron:0 * * * * *}")
    public void processStaged() {
        if (perFarmLockEnabled) {
            processStagedPerFarm();
        } else {
            processStagedGlobal();
        }
    }

    private void processStagedGlobal() {
        lock.runIfLocked(LOCK_KEY, LOCK_TIMEOUT, () -> {
            List<StagedFeedingEvent> due = stagedRepo.findDueForProcessing(
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
                List<StagedFeedingEvent> due = stagedRepo.findDueForProcessing(
                        farmId, LocalDateTime.now(),
                        StagedStatus.PENDING, StagedStatus.RETRYING,
                        PageRequest.of(0, batchSize));
                runBatch(due);
                return null;
            });
        }
    }

    private void runBatch(List<StagedFeedingEvent> due) {
        if (due.isEmpty()) return;
        log.info("StagedFeedingEventValidator: {} row(s) due", due.size());
        for (StagedFeedingEvent s : due) {
            processRow(s.getId());
        }
    }

    public void processOneImmediately(UUID id) {
        processRow(id);
    }

    private void processRow(UUID id) {
        try {
            txTemplate.execute(status -> { tryProcessOne(id); return null; });
        } catch (Exception ex) {
            log.warn("Staged feeding-event {} failed (transient): {}", id, ex.getMessage());
            try {
                txTemplate.execute(status -> { recordFailure(id, ex.getMessage()); return null; });
            } catch (Exception recordEx) {
                log.error("Failed to record failure for {}: {}", id, recordEx.getMessage(), recordEx);
            }
        }
    }

    private void tryProcessOne(UUID id) {
        StagedFeedingEvent staged = stagedRepo.findByIdForUpdate(id).orElse(null);
        if (staged == null || !isDue(staged, LocalDateTime.now())) return;

        staged.setLastAttemptAt(LocalDateTime.now());

        try {
            FeedingSubmissionPayload payload = objectMapper.readValue(
                    staged.getPayloadJson(), FeedingSubmissionPayload.class);

            if (payload.quantityKg() == null || payload.quantityKg().signum() <= 0) {
                throw new BusinessException("quantityKg must be positive");
            }
            if (payload.fedAt() == null || payload.fedAt().isAfter(java.time.LocalDate.now())) {
                throw new BusinessException("fedAt cannot be in the future");
            }

            FeedConsumerPort port = consumerPorts.stream()
                    .filter(p -> p.consumerType() == payload.consumerType())
                    .findFirst()
                    .orElseThrow(() -> new UnregisteredConsumerTypeException(payload.consumerType().name()));

            UUID consumerId = port.resolveConsumerIdByDisplayName(
                            staged.getFarmId(), payload.consumerDisplayName())
                    .orElseThrow(() -> new BusinessException(
                            "Could not resolve consumer '" + payload.consumerDisplayName()
                                    + "' on this farm"));

            FeedingRequest req = new FeedingRequest();
            req.setConsumerType(payload.consumerType());
            req.setConsumerId(consumerId);
            req.setQuantityKg(payload.quantityKg());
            req.setFedAt(payload.fedAt());
            req.setNotes(payload.notes());

            FeedingEventResponse created = consumptionService.feed(
                    req, staged.getFarmId(), staged.getSubmittedByUserId());

            staged.setStatus(StagedStatus.MOVED);
            staged.setMovedFeedingId(created.getId());
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            staged.setValidationErrors(null);
            stagedRepo.save(staged);

            log.info("Staged feeding-event {} MOVED -> event {}", staged.getId(), created.getId());

        } catch (JsonProcessingException e) {
            markTerminal(staged, "Invalid JSON payload: " + e.getMessage());
        } catch (UnregisteredConsumerTypeException | ConsumerNotFoundInDomainException | BusinessException e) {
            markTerminal(staged, e.getMessage());
        }
    }

    private void markTerminal(StagedFeedingEvent staged, String msg) {
        int current = staged.getRetryCount() == null ? 0 : staged.getRetryCount();
        int next = Math.min(current + 1, maxAttempts);            // Stage 2 / R5
        staged.setRetryCount(next);
        staged.setValidationErrors(truncate(msg));
        staged.setStatus(StagedStatus.FAILED);
        staged.setProcessedAt(LocalDateTime.now());
        staged.setNextRetryAt(null);
        stagedRepo.save(staged);
        log.warn("Staged feeding-event {} FAILED (terminal): {}", staged.getId(), msg);
    }

    private void recordFailure(UUID id, String message) {
        StagedFeedingEvent staged = stagedRepo.findByIdForUpdate(id).orElse(null);
        if (staged == null) return;

        // Stage 2 / R2: the previous tx rolled back, so lastAttemptAt was lost.
        staged.setLastAttemptAt(LocalDateTime.now());

        int current = staged.getRetryCount() == null ? 0 : staged.getRetryCount();
        int attempt = Math.min(current + 1, maxAttempts);         // Stage 2 / R5
        staged.setRetryCount(attempt);
        staged.setValidationErrors(truncate(message));

        if (attempt >= maxAttempts) {
            staged.setStatus(StagedStatus.FAILED);
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            log.error("Staged feeding-event {} FAILED after {} attempts: {}",
                    staged.getId(), attempt, message);
        } else {
            staged.setStatus(StagedStatus.RETRYING);
            staged.setNextRetryAt(LocalDateTime.now().plusMinutes(retryDelayMinutes));
            log.warn("Staged feeding-event {} RETRY {}/{} — {}",
                    staged.getId(), attempt, maxAttempts, message);
        }
        stagedRepo.save(staged);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= ERROR_FIELD_LIMIT ? s : s.substring(0, ERROR_FIELD_LIMIT);
    }

    private static boolean isDue(StagedFeedingEvent s, LocalDateTime now) {
        if (s.getStatus() == StagedStatus.PENDING) return true;
        if (s.getStatus() != StagedStatus.RETRYING) return false;
        return s.getNextRetryAt() == null || !s.getNextRetryAt().isAfter(now);
    }
}