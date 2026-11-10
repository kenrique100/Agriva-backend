package com.agriva.finance.kafka.producer;

import com.agriva.shared.kafka.BaseKafkaProducer;
import com.agriva.shared.kafka.KafkaPublishService;
import com.agriva.shared.kafka.topics.IncomeKafkaConfig;
import com.agriva.finance.model.dto.request.IncomeSubmissionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class IncomeProducer extends BaseKafkaProducer<IncomeSubmissionEvent> {

    public IncomeProducer(KafkaPublishService publishService,
                          ObjectMapper objectMapper) {
        super(publishService, IncomeKafkaConfig.STAGING_TOPIC, objectMapper);
    }

    public void publish(IncomeSubmissionEvent event) {
        String key = event.getFarmId() != null
                ? event.getFarmId().toString()
                : event.getSubmissionId().toString();
        publish(key, event);
    }
}