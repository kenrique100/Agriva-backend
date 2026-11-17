package com.agriva.feeding.kafka.producer;

import com.agriva.feeding.model.dto.request.FeedIngredientSubmissionEvent;
import com.agriva.shared.kafka.BaseKafkaProducer;
import com.agriva.shared.kafka.KafkaPublishService;
import com.agriva.shared.kafka.topics.FeedIngredientKafkaConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class FeedIngredientProducer extends BaseKafkaProducer<FeedIngredientSubmissionEvent> {

    public FeedIngredientProducer(KafkaPublishService publishService,
                                  ObjectMapper objectMapper) {
        super(publishService, FeedIngredientKafkaConfig.STAGING_TOPIC, objectMapper);
    }

    public void publish(FeedIngredientSubmissionEvent event) {
        String key = event.getFarmId() != null
                ? event.getFarmId().toString()
                : event.getSubmissionId().toString();
        publish(key, event);
    }
}