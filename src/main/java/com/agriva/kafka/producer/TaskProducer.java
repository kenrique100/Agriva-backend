package com.agriva.kafka.producer;

import com.agriva.config.TaskKafkaConfig;
import com.agriva.model.dto.task.TaskSubmissionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(TaskSubmissionEvent event) {
        String key = event.getFarmId() != null
                ? event.getFarmId().toString()
                : event.getSubmissionId().toString();
        kafkaTemplate.send(TaskKafkaConfig.STAGING_TOPIC, key, event)
                .whenComplete((r, ex) -> {
                    if (ex != null) log.error("Failed to publish task submission {}: {}",
                            event.getSubmissionId(), ex.getMessage());
                    else log.info("Published task submission {} partition={} offset={}",
                            event.getSubmissionId(),
                            r.getRecordMetadata().partition(),
                            r.getRecordMetadata().offset());
                });
    }
}