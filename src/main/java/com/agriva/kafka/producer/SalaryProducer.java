package com.agriva.kafka.producer;

import com.agriva.config.SalaryKafkaConfig;
import com.agriva.model.dto.salary.SalarySubmissionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SalaryProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(SalarySubmissionEvent event) {
        String key = event.getFarmId() != null
                ? event.getFarmId().toString() : event.getSubmissionId().toString();
        kafkaTemplate.send(SalaryKafkaConfig.STAGING_TOPIC, key, event)
                .whenComplete((r, ex) -> {
                    if (ex != null) log.error("Failed to publish salary submission {}: {}",
                            event.getSubmissionId(), ex.getMessage());
                    else log.info("Published salary submission {} partition={} offset={}",
                            event.getSubmissionId(),
                            r.getRecordMetadata().partition(),
                            r.getRecordMetadata().offset());
                }).join();
    }
}