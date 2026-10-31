package com.agriva.kafka.producer;

import com.agriva.config.SalaryKafkaConfig;
import com.agriva.model.dto.salary.SalarySubmissionEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class SalaryProducer extends BaseKafkaProducer<SalarySubmissionEvent> {

    public SalaryProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        super(kafkaTemplate, SalaryKafkaConfig.STAGING_TOPIC);
    }

    public void publish(SalarySubmissionEvent event) {
        String key = event.getFarmId() != null
                ? event.getFarmId().toString()
                : event.getSubmissionId().toString();
        publish(key, event);
    }
}