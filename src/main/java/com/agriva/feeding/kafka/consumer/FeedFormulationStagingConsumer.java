package com.agriva.feeding.kafka.consumer;

import com.agriva.feeding.model.dto.request.FeedFormulationSubmissionEvent;
import com.agriva.feeding.model.entity.StagedFeedingFormulation;
import com.agriva.feeding.model.enums.StagedFeedingStatus;
import com.agriva.feeding.repository.StagedFeedingFormulationRepository;
import com.agriva.feeding.scheduler.StagedFeedingFormulationValidator;
import com.agriva.shared.kafka.IdempotencyGuard;
import com.agriva.shared.kafka.topics.FeedFormulationKafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class FeedFormulationStagingConsumer {

    private final StagedFeedingFormulationRepository stagedRepo;
    private final IdempotencyGuard idempotencyGuard;
    private final StagedFeedingFormulationValidator validator;

    @KafkaListener(
            topics = FeedFormulationKafkaConfig.STAGING_TOPIC,
            groupId = FeedFormulationKafkaConfig.STAGING_CONSUMER_GROUP,
            containerFactory = "feedFormulationKafkaListenerContainerFactory")
    @SuppressWarnings("unused")
    public void onEvent(FeedFormulationSubmissionEvent event) {
        if (event == null || event.getSubmissionId() == null
                || event.getPayloadJson() == null) {
            log.warn("Skipping malformed feeding-formulation event: {}", event);
            return;
        }
        if (!idempotencyGuard.tryMarkProcessed(
                event.getSubmissionId(), FeedFormulationKafkaConfig.STAGING_CONSUMER_GROUP)) {
            log.info("Duplicate feeding-formulation event {} — skipping",
                    event.getSubmissionId());
            return;
        }

        StagedFeedingFormulation staged = StagedFeedingFormulation.builder()
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
            log.info("Staged feeding-formulation {} persisted", staged.getId());
        } catch (DataIntegrityViolationException e) {
            log.error("Staged feeding-formulation {} FK violation, routing to DLQ: {}",
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