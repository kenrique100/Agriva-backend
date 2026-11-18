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
import com.agriva.shared.exception.InsufficientFeedStoreException;
import com.agriva.shared.exception.UnregisteredConsumerTypeException;
import com.agriva.shared.models.enums.StagedStatus;
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
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StagedFeedingEventValidator {

    private static final int ERROR_FIELD_LIMIT = 1990;

    private final StagedFeedingEventRepository stagedRepo;
    private final List<FeedConsumerPort> consumerPorts;
    private final FeedConsumptionService consumptionService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;

    @Value("${agriva.feeding.feeding.validator.batch-size:100}")
    private int batchSize;
    @Value("${agriva.feeding.feeding.validator.max-attempts:4}")
    private int maxAttempts;
    @Value("${agriva.feeding.feeding.validator.retry-delay-minutes:1}")
    private long retryDelayMinutes;

    @Scheduled(cron = "${agriva.feeding.feeding.validator.cron:0 * * * * *}")
    public void processStaged() {
        List<StagedFeedingEvent> due = stagedRepo.findDueForProcessing(
                LocalDateTime.now(),
                StagedStatus.PENDING,
                StagedStatus.RETRYING,
                PageRequest.of(0, batchSize));
        if (due.isEmpty()) return;
        log.info("StagedFeedingEventValidator: {} row(s) due", due.size());
        for (StagedFeedingEvent s : due) {
            final UUID id = s.getId();
            txTemplate.execute(status -> { processOne(id); return null; });
        }
    }

    private void processOne(UUID id) {
        StagedFeedingEvent staged = stagedRepo.findByIdForUpdate(id).orElse(null);
        if (staged == null || !isDue(staged, LocalDateTime.now())) return;
        staged.setLastAttemptAt(LocalDateTime.now());
        try {
            FeedingSubmissionPayload payload = objectMapper.readValue(
                    staged.getPayloadJson(), FeedingSubmissionPayload.class);

            FeedConsumerPort port = consumerPorts.stream()
                    .filter(p -> p.consumerType() == payload.consumerType())
                    .findFirst()
                    .orElseThrow(() -> new UnregisteredConsumerTypeException(
                            payload.consumerType().name()));

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
            log.info("Staged feeding-event {} MOVED -> event {}",
                    staged.getId(), created.getId());
        } catch (UnregisteredConsumerTypeException
                 | ConsumerNotFoundInDomainException
                 | BusinessException e) {
            // Permanent — misconfiguration or bad data
            markFailed(staged, e.getMessage());
        } catch (InsufficientFeedStoreException e) {
            // Transient — store may be replenished
            onFailure(staged, List.of(e.getMessage()));
        } catch (Exception e) {
            log.error("Staged feeding-event {} threw: {}",
                    staged.getId(), e.getMessage(), e);
            onFailure(staged, List.of("Processing error: " + e.getMessage()));
        }
    }

    private void markFailed(StagedFeedingEvent staged, String msg) {
        staged.setRetryCount((staged.getRetryCount() == null ? 0 : staged.getRetryCount()) + 1);
        staged.setValidationErrors(truncate(msg));
        staged.setStatus(StagedStatus.FAILED);
        staged.setProcessedAt(LocalDateTime.now());
        staged.setNextRetryAt(null);
        stagedRepo.save(staged);
    }

    private void onFailure(StagedFeedingEvent staged, List<String> errors) {
        int attempt = (staged.getRetryCount() == null ? 0 : staged.getRetryCount()) + 1;
        staged.setRetryCount(attempt);
        staged.setValidationErrors(truncate(String.join("; ", errors)));
        if (attempt >= maxAttempts) {
            staged.setStatus(StagedStatus.FAILED);
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            log.error("Staged feeding-event {} FAILED after {} attempts: {}",
                    staged.getId(), attempt, errors);
        } else {
            staged.setStatus(StagedStatus.RETRYING);
            staged.setNextRetryAt(LocalDateTime.now().plusMinutes(retryDelayMinutes));
            log.warn("Staged feeding-event {} RETRY {}/{} — {}",
                    staged.getId(), attempt, maxAttempts, errors);
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
    public void processOneImmediately(UUID id) {
        txTemplate.execute(status -> { processOne(id); return null; });
    }
}