package com.agriva.feeding.kafka.consumer;

import com.agriva.feeding.model.dto.request.FeedProductionSubmissionEvent;
import com.agriva.feeding.scheduler.StagedFeedingProductionValidator;
import com.agriva.feeding.service.FeedProductionStagingService;
import com.agriva.shared.kafka.topics.FeedProductionKafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FeedProductionStagingConsumer {

    private final FeedProductionStagingService stagingService;
    private final StagedFeedingProductionValidator validator;

    @Value("${agriva.feeding.production.consumer.immediate-processing.enabled:true}")
    private boolean immediateProcessingEnabled;

    @KafkaListener(
            topics = FeedProductionKafkaConfig.STAGING_TOPIC,
            groupId = FeedProductionKafkaConfig.STAGING_CONSUMER_GROUP,
            containerFactory = "feedProductionKafkaListenerContainerFactory")
    public void onEvent(FeedProductionSubmissionEvent event) {
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