package com.agriva.kafka.producer;

import com.agriva.config.ExpenseKafkaConfig;
import com.agriva.model.dto.expense.ExpenseSubmissionEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ExpenseProducer extends BaseKafkaProducer<ExpenseSubmissionEvent> {

    public ExpenseProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        super(kafkaTemplate, ExpenseKafkaConfig.STAGING_TOPIC);
    }

    public void publish(ExpenseSubmissionEvent event) {
        String key = event.getFarmId() != null
                ? event.getFarmId().toString()
                : event.getSubmissionId().toString();
        publish(key, event);
    }
}