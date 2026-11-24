package com.agriva.feeding.kafka.consumer;

import com.agriva.feeding.model.dto.request.FeedIngredientSubmissionEvent;
import com.agriva.feeding.scheduler.StagedFeedingIngredientValidator;
import com.agriva.feeding.service.IngredientStagingService;
import com.agriva.shared.kafka.topics.FeedIngredientKafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FeedIngredientStagingConsumer {

    private final IngredientStagingService stagingService;
    private final StagedFeedingIngredientValidator validator;

    @Value("${agriva.feeding.ingredient.consumer.immediate-processing.enabled:true}")
    private boolean immediateProcessingEnabled;

    @KafkaListener(
            topics = FeedIngredientKafkaConfig.STAGING_TOPIC,
            groupId = FeedIngredientKafkaConfig.STAGING_CONSUMER_GROUP,
            containerFactory = "feedIngredientKafkaListenerContainerFactory")
    public void onEvent(FeedIngredientSubmissionEvent event) {
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