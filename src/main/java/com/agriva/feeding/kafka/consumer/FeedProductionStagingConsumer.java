package com.agriva.feeding.kafka.consumer;

import com.agriva.feeding.model.dto.request.FeedProductionSubmissionEvent;
import com.agriva.feeding.model.entity.StagedFeedingProduction;
import com.agriva.feeding.repository.StagedFeedingProductionRepository;
import com.agriva.feeding.scheduler.StagedFeedingProductionValidator;
import com.agriva.shared.kafka.IdempotencyGuard;
import com.agriva.shared.kafka.topics.FeedProductionKafkaConfig;
import com.agriva.shared.models.enums.StagedStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class FeedProductionStagingConsumer {

    private final StagedFeedingProductionRepository stagedRepo;
    private final IdempotencyGuard idempotencyGuard;
    private final StagedFeedingProductionValidator validator;

    @KafkaListener(
            topics = FeedProductionKafkaConfig.STAGING_TOPIC,
            groupId = FeedProductionKafkaConfig.STAGING_CONSUMER_GROUP,
            containerFactory = "feedProductionKafkaListenerContainerFactory")
    @SuppressWarnings("unused")
    public void onEvent(FeedProductionSubmissionEvent event) {
        if (event == null || event.getSubmissionId() == null
                || event.getPayloadJson() == null) {
            log.warn("Skipping malformed feeding-production event: {}", event);
            return;
        }
        if (!idempotencyGuard.tryMarkProcessed(
                event.getSubmissionId(), FeedProductionKafkaConfig.STAGING_CONSUMER_GROUP)) {
            log.info("Duplicate feeding-production event {} — skipping",
                    event.getSubmissionId());
            return;
        }

        StagedFeedingProduction staged = StagedFeedingProduction.builder()
                .id(event.getSubmissionId())
                .farmId(event.getFarmId())
                .payloadJson(event.getPayloadJson())
                .status(StagedStatus.PENDING)
                .submittedByUserId(event.getSubmittedByUserId())
                .submittedByEmail(event.getSubmittedByEmail())
                .submittedAt(event.getSubmittedAt() != null
                        ? event.getSubmittedAt() : LocalDateTime.now())
                .retryCount(0)
                .build();

        try {
            stagedRepo.saveAndFlush(staged);
            log.info("Staged feeding-production {} persisted", staged.getId());
        } catch (DataIntegrityViolationException e) {
            log.error("Staged feeding-production {} FK violation, routing to DLQ: {}",
                    staged.getId(), e.getMessage());
            throw e;
        }

        try {
            validator.processOneImmediately(staged.getId());
        } catch (Exception e) {
            log.warn("Immediate processing failed for {} — cron will retry: {}",
                    staged.getId(), e.getMessage());
        }
    }
}