package com.agriva.feeding.kafka.consumer;

import com.agriva.feeding.model.dto.request.FeedFeedingSubmissionEvent;
import com.agriva.feeding.scheduler.StagedFeedingEventValidator;
import com.agriva.feeding.service.FeedFeedingStagingService;
import com.agriva.shared.kafka.topics.FeedFeedingKafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FeedFeedingStagingConsumer {

    private final FeedFeedingStagingService stagingService;
    private final StagedFeedingEventValidator validator;

    @Value("${agriva.feeding.feeding.consumer.immediate-processing.enabled:true}")
    private boolean immediateProcessingEnabled;

    @KafkaListener(
            topics = FeedFeedingKafkaConfig.STAGING_TOPIC,
            groupId = FeedFeedingKafkaConfig.STAGING_CONSUMER_GROUP,
            containerFactory = "feedFeedingKafkaListenerContainerFactory")
    public void onEvent(FeedFeedingSubmissionEvent event) {
        boolean persisted = stagingService.persistStaged(event);
        if (!persisted) return;

        if (!immediateProcessingEnabled) {
            log.debug("Immediate processing disabled — {} left PENDING for scheduled worker",
                    event.getSubmissionId());
            return;
        }

        try {
            validator.processOneImmediately(event.getSubmissionId());
        } catch (Exception e) {
            log.warn("Immediate processing failed for {} — cron will retry: {}",
                    event.getSubmissionId(), e.getMessage());
        }
    }
}