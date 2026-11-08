package com.agriva.farming.kafka.producer;

import com.agriva.shared.kafka.BaseKafkaProducer;
import com.agriva.shared.kafka.topics.CropKafkaConfig;
import com.agriva.farming.model.dto.requests.CropCycleSubmissionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CropCycleProducer extends BaseKafkaProducer<CropCycleSubmissionEvent> {

    public CropCycleProducer(KafkaTemplate<String, Object> kafkaTemplate,
                             ObjectMapper objectMapper) {
        super(kafkaTemplate, CropKafkaConfig.STAGING_TOPIC, objectMapper);
    }

    public void publish(CropCycleSubmissionEvent event) {
        String key = event.getFarmId() != null
                ? event.getFarmId().toString()
                : event.getSubmissionId().toString();
        publish(key, event);
    }
}