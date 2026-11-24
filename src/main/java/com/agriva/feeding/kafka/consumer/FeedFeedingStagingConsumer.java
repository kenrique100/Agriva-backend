package com.agriva.feeding.kafka.consumer;

import com.agriva.feeding.model.dto.request.FeedFeedingSubmissionEvent;
import com.agriva.feeding.model.entity.StagedFeedingEvent;
import com.agriva.feeding.repository.StagedFeedingEventRepository;
import com.agriva.feeding.scheduler.StagedFeedingEventValidator;
import com.agriva.shared.kafka.topics.FeedFeedingKafkaConfig;
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
public class FeedFeedingStagingConsumer {

    private final StagedFeedingEventRepository stagedRepo;
    private final StagedFeedingEventValidator validator;

    @KafkaListener(
            topics = FeedFeedingKafkaConfig.STAGING_TOPIC,
            groupId = FeedFeedingKafkaConfig.STAGING_CONSUMER_GROUP,
            containerFactory = "feedFeedingKafkaListenerContainerFactory")
    @SuppressWarnings("unused")
    public void onEvent(FeedFeedingSubmissionEvent event) {
        if (event == null || event.getSubmissionId() == null || event.getPayloadJson() == null) {
            log.warn("Skipping malformed feeding-event: {}", event);
            return;
        }

        StagedFeedingEvent staged = StagedFeedingEvent.builder()
                .id(event.getSubmissionId())
                .farmId(event.getFarmId())
                .payloadJson(event.getPayloadJson())
                .status(StagedStatus.PENDING)
                .submittedByUserId(event.getSubmittedByUserId())
                .submittedByEmail(event.getSubmittedByEmail())
                .submittedAt(event.getSubmittedAt() != null ? event.getSubmittedAt() : LocalDateTime.now())
                .batchId(event.getBatchId())
                .rowNumber(event.getRowNumber())
                .retryCount(0)
                .build();

        try {
            stagedRepo.saveAndFlush(staged);
            log.info("Staged feeding-event {} persisted (batch={}, row={})",
                    staged.getId(), staged.getBatchId(), staged.getRowNumber());
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate feeding-event {} — skipping (already staged)", event.getSubmissionId());
            return;
        }

        try {
            validator.processOneImmediately(staged.getId());
        } catch (Exception e) {
            log.warn("Immediate processing failed for {} — cron will retry: {}",
                    staged.getId(), e.getMessage());
        }
    }
}