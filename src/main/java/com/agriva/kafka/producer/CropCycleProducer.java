package com.agriva.kafka.producer;

import com.agriva.config.CropKafkaConfig;
import com.agriva.model.dto.crop.CropCycleSubmissionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor @Slf4j
public class CropCycleProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;
    public void publish(CropCycleSubmissionEvent event) {
        String key = event.getFarmId() != null ? event.getFarmId().toString() : event.getSubmissionId().toString();
        kafkaTemplate.send(CropKafkaConfig.STAGING_TOPIC, key, event)
                .whenComplete((r, ex) -> {
                    if (ex != null) log.error("Failed to publish crop cycle {}", event.getSubmissionId());
                });
    }
}