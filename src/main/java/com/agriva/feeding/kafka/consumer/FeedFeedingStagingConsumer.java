package com.agriva.feeding.kafka.consumer;

import com.agriva.feeding.model.dto.request.FeedFeedingSubmissionEvent;
import com.agriva.feeding.model.entity.StagedFeedingEvent;
import com.agriva.feeding.model.enums.StagedFeedingStatus;
import com.agriva.feeding.repository.StagedFeedingEventRepository;
import com.agriva.shared.kafka.IdempotencyGuard;
import com.agriva.shared.kafka.topics.FeedFeedingKafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class FeedFeedingStagingConsumer {

    private final StagedFeedingEventRepository stagedRepo;
    private final IdempotencyGuard idempotencyGuard;

    @KafkaListener(
            topics = FeedFeedingKafkaConfig.STAGING_TOPIC,
            groupId = FeedFeedingKafkaConfig.STAGING_CONSUMER_GROUP,
            containerFactory = "feedFeedingKafkaListenerContainerFactory")
    public void onEvent(FeedFeedingSubmissionEvent event) {
        if (event == null || event.getSubmissionId() == null
                || event.getPayloadJson() == null) {
            log.warn("Skipping malformed feeding-event: {}", event);
            return;
        }
        if (!idempotencyGuard.tryMarkProcessed(
                event.getSubmissionId(), FeedFeedingKafkaConfig.STAGING_CONSUMER_GROUP)) {
            log.info("Duplicate feeding-event {} — skipping", event.getSubmissionId());
            return;
        }
        StagedFeedingEvent staged = StagedFeedingEvent.builder()
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
            stagedRepo.save(staged);
            log.info("Staged feeding-event {} persisted", staged.getId());
        } catch (DataIntegrityViolationException e) {
            log.error("Staged feeding-event {} FK violation, routing to DLQ: {}",
                    staged.getId(), e.getMessage());
            throw e;
        }
    }
}