package com.agriva.kafka.producer;

import com.agriva.config.CropKafkaConfig;
import com.agriva.model.dto.crop.CropCycleSubmissionEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CropCycleProducer extends BaseKafkaProducer<CropCycleSubmissionEvent> {

    public CropCycleProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        super(kafkaTemplate, CropKafkaConfig.STAGING_TOPIC);
    }

    public void publish(CropCycleSubmissionEvent event) {
        String key = event.getFarmId() != null
                ? event.getFarmId().toString()
                : event.getSubmissionId().toString();
        publish(key, event);
    }
}