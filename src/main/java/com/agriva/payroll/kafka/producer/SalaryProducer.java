package com.agriva.payroll.kafka.producer;

import com.agriva.shared.kafka.BaseKafkaProducer;
import com.agriva.shared.kafka.topics.SalaryKafkaConfig;
import com.agriva.payroll.model.dto.requests.SalarySubmissionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class SalaryProducer extends BaseKafkaProducer<SalarySubmissionEvent> {

    public SalaryProducer(KafkaTemplate<String, Object> kafkaTemplate,
                          ObjectMapper objectMapper) {
        super(kafkaTemplate, SalaryKafkaConfig.STAGING_TOPIC, objectMapper);
    }

    public void publish(SalarySubmissionEvent event) {
        String key = event.getFarmId() != null
                ? event.getFarmId().toString()
                : event.getSubmissionId().toString();
        publish(key, event);
    }
}