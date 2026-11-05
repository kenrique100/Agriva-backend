package com.agriva.task.kafka.producer;

import com.agriva.shared.kafka.BaseKafkaProducer;
import com.agriva.shared.kafka.topics.TaskKafkaConfig;
import com.agriva.task.model.dto.requests.TaskSubmissionEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class TaskProducer extends BaseKafkaProducer<TaskSubmissionEvent> {

    public TaskProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        super(kafkaTemplate, TaskKafkaConfig.STAGING_TOPIC);
    }

    public void publish(TaskSubmissionEvent event) {
        String key = event.getFarmId() != null
                ? event.getFarmId().toString()
                : event.getSubmissionId().toString();
        publish(key, event);
    }
}