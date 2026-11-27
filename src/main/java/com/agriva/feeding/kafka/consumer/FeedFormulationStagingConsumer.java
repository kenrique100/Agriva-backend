package com.agriva.feeding.kafka.consumer;

import com.agriva.feeding.model.dto.request.FeedFormulationSubmissionEvent;
import com.agriva.feeding.scheduler.StagedFeedingFormulationValidator;
import com.agriva.feeding.service.FeedFormulationStagingService;
import com.agriva.shared.kafka.topics.FeedFormulationKafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FeedFormulationStagingConsumer {

    private final FeedFormulationStagingService stagingService;
    private final StagedFeedingFormulationValidator validator;

    @Value("${agriva.feeding.formulation.consumer.immediate-processing.enabled:true}")
    private boolean immediateProcessingEnabled;

    @KafkaListener(
            topics = FeedFormulationKafkaConfig.STAGING_TOPIC,
            groupId = FeedFormulationKafkaConfig.STAGING_CONSUMER_GROUP,
            containerFactory = "feedFormulationKafkaListenerContainerFactory")
    public void onEvent(FeedFormulationSubmissionEvent event) {
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