package com.agriva.feeding.kafka.consumer;

import com.agriva.feeding.model.dto.request.FeedIngredientSubmissionEvent;
import com.agriva.feeding.model.entity.StagedFeedingIngredient;
import com.agriva.feeding.model.enums.StagedFeedingStatus;
import com.agriva.feeding.repository.StagedFeedingIngredientRepository;
import com.agriva.feeding.scheduler.StagedFeedingIngredientValidator;
import com.agriva.shared.kafka.IdempotencyGuard;
import com.agriva.shared.kafka.topics.FeedIngredientKafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class FeedIngredientStagingConsumer {

    private final StagedFeedingIngredientRepository stagedRepo;
    private final IdempotencyGuard idempotencyGuard;
    private final StagedFeedingIngredientValidator validator;

    @KafkaListener(
            topics = FeedIngredientKafkaConfig.STAGING_TOPIC,
            groupId = FeedIngredientKafkaConfig.STAGING_CONSUMER_GROUP,
            containerFactory = "feedIngredientKafkaListenerContainerFactory")
    @SuppressWarnings("unused")   // called by Kafka via reflection
    public void onEvent(FeedIngredientSubmissionEvent event) {
        if (event == null || event.getSubmissionId() == null
                || event.getPayloadJson() == null) {
            log.warn("Skipping malformed feeding-ingredient event: {}", event);
            return;
        }
        if (!idempotencyGuard.tryMarkProcessed(
                event.getSubmissionId(), FeedIngredientKafkaConfig.STAGING_CONSUMER_GROUP)) {
            log.info("Duplicate delivery of feeding-ingredient event {} — skipping",
                    event.getSubmissionId());
            return;
        }

        StagedFeedingIngredient staged = StagedFeedingIngredient.builder()
                .id(event.getSubmissionId())
                .farmId(event.getFarmId())
                .payloadJson(event.getPayloadJson())
                .status(StagedFeedingStatus.PENDING)
                .submittedByUserId(event.getSubmittedByUserId())
                .submittedByEmail(event.getSubmittedByEmail())
                .submittedAt(event.getSubmittedAt() != null
                        ? event.getSubmittedAt() : LocalDateTime.now())
                .retryCount(0)
                .build();

        try {
            stagedRepo.saveAndFlush(staged);
            log.info("Staged feeding-ingredient {} persisted", staged.getId());
        } catch (DataIntegrityViolationException e) {
            log.error("Staged feeding-ingredient {} FK violation, routing to DLQ: {}",
                    staged.getId(), e.getMessage());
            throw e;
        }

        // Trigger immediate processing. This makes the pipeline sub-second
        // instead of waiting for the cron tick. The cron remains as a safety
        // net for retries and any rows that failed here transiently.
        try {
            validator.processOneImmediately(staged.getId());
        } catch (Exception e) {
            // The row is already persisted; the cron will pick it up within 60 s.
            log.warn("Immediate processing failed for {} — cron will retry: {}",
                    staged.getId(), e.getMessage());
        }
    }
}