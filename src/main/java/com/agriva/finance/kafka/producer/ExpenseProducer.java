package com.agriva.finance.kafka.producer;

import com.agriva.shared.kafka.BaseKafkaProducer;
import com.agriva.shared.kafka.KafkaPublishService;
import com.agriva.shared.kafka.topics.ExpenseKafkaConfig;
import com.agriva.finance.model.dto.request.ExpenseSubmissionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class ExpenseProducer extends BaseKafkaProducer<ExpenseSubmissionEvent> {

    public ExpenseProducer(KafkaPublishService publishService,
                           ObjectMapper objectMapper) {
        super(publishService, ExpenseKafkaConfig.STAGING_TOPIC, objectMapper);
    }

    public void publish(ExpenseSubmissionEvent event) {
        String key = event.getFarmId() != null
                ? event.getFarmId().toString()
                : event.getSubmissionId().toString();
        publish(key, event);
    }
}