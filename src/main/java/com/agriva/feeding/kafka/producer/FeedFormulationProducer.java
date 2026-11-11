package com.agriva.feeding.kafka.producer;

import com.agriva.feeding.model.dto.request.FeedFormulationSubmissionEvent;
import com.agriva.shared.kafka.BaseKafkaProducer;
import com.agriva.shared.kafka.KafkaPublishService;
import com.agriva.shared.kafka.topics.FeedFormulationKafkaConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class FeedFormulationProducer extends BaseKafkaProducer<FeedFormulationSubmissionEvent> {

    public FeedFormulationProducer(KafkaPublishService publishService,
                                   ObjectMapper objectMapper) {
        super(publishService, FeedFormulationKafkaConfig.STAGING_TOPIC, objectMapper);
    }

    public void publish(FeedFormulationSubmissionEvent event) {
        String key = event.getFarmId() != null
                ? event.getFarmId().toString()
                : event.getSubmissionId().toString();
        publish(key, event);
    }
}