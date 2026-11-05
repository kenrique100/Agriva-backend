package com.agriva.finance.kafka.producer;

import com.agriva.shared.kafka.BaseKafkaProducer;
import com.agriva.shared.kafka.topics.ExpenseKafkaConfig;
import com.agriva.finance.model.dto.request.ExpenseSubmissionEvent;
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