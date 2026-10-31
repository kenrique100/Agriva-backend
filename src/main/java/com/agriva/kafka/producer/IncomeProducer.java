package com.agriva.kafka.producer;

import com.agriva.config.IncomeKafkaConfig;
import com.agriva.model.dto.income.IncomeSubmissionEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class IncomeProducer extends BaseKafkaProducer<IncomeSubmissionEvent> {

    public IncomeProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        super(kafkaTemplate, IncomeKafkaConfig.STAGING_TOPIC);
    }

    public void publish(IncomeSubmissionEvent event) {
        String key = event.getFarmId() != null
                ? event.getFarmId().toString()
                : event.getSubmissionId().toString();
        publish(key, event);
    }
}