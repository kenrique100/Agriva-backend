package com.agriva.feeding.kafka.producer;

import com.agriva.feeding.model.dto.request.FeedFeedingSubmissionEvent;
import com.agriva.shared.kafka.BaseKafkaProducer;
import com.agriva.shared.kafka.KafkaPublishService;
import com.agriva.shared.kafka.topics.FeedFeedingKafkaConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class FeedFeedingProducer extends BaseKafkaProducer<FeedFeedingSubmissionEvent> {

    public FeedFeedingProducer(KafkaPublishService publishService,
                               ObjectMapper objectMapper) {
        super(publishService, FeedFeedingKafkaConfig.STAGING_TOPIC, objectMapper);
    }

    public void publish(FeedFeedingSubmissionEvent event) {
        String key = event.getFarmId() != null
                ? event.getFarmId().toString()
                : event.getSubmissionId().toString();
        publish(key, event);
    }
}