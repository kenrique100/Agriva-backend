package com.agriva.kafka.producer;

import com.agriva.config.IncomeKafkaConfig;
import com.agriva.model.dto.income.IncomeSubmissionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class IncomeProducer {

    /**
     * Declared as <String, Object> to match the bean defined in KafkaProducerConfig.
     * The event object is JSON-serialized by the configured JsonSerializer;
     * the generic parameter on KafkaTemplate has no runtime effect on the payload.
     */
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(IncomeSubmissionEvent event) {
        String key = event.getFarmId() != null
                ? event.getFarmId().toString()
                : event.getSubmissionId().toString();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(IncomeKafkaConfig.STAGING_TOPIC, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish income submission {} to Kafka: {}",
                        event.getSubmissionId(), ex.getMessage(), ex);
            } else if (result != null) {
                log.info("Published income submission {} to Kafka. Partition={}, Offset={}",
                        event.getSubmissionId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}