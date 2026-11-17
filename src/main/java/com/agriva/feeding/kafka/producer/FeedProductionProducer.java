package com.agriva.feeding.kafka.producer;

import com.agriva.feeding.model.dto.request.FeedProductionSubmissionEvent;
import com.agriva.shared.kafka.BaseKafkaProducer;
import com.agriva.shared.kafka.KafkaPublishService;
import com.agriva.shared.kafka.topics.FeedProductionKafkaConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class FeedProductionProducer extends BaseKafkaProducer<FeedProductionSubmissionEvent> {

    public FeedProductionProducer(KafkaPublishService publishService,
                                  ObjectMapper objectMapper) {
        super(publishService, FeedProductionKafkaConfig.STAGING_TOPIC, objectMapper);
    }

    public void publish(FeedProductionSubmissionEvent event) {
        String key = event.getFarmId() != null
                ? event.getFarmId().toString()
                : event.getSubmissionId().toString();
        publish(key, event);
    }
}