package com.agriva.kafka.producer;

import com.agriva.config.ExpenseKafkaConfig;
import com.agriva.model.dto.expense.ExpenseSubmissionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpenseProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(ExpenseSubmissionEvent event) {
        String key = event.getFarmId() != null
                ? event.getFarmId().toString()
                : event.getSubmissionId().toString();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(ExpenseKafkaConfig.STAGING_TOPIC, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish expense submission {} to Kafka: {}",
                        event.getSubmissionId(), ex.getMessage(), ex);
            } else if (result != null) {
                log.info("Published expense submission {} to Kafka. Partition={}, Offset={}",
                        event.getSubmissionId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}