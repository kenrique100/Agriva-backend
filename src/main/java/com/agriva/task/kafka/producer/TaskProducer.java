package com.agriva.task.kafka.producer;

import com.agriva.shared.kafka.BaseKafkaProducer;
import com.agriva.shared.kafka.KafkaPublishService;
import com.agriva.shared.kafka.topics.TaskKafkaConfig;
import com.agriva.task.model.dto.requests.TaskSubmissionEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class TaskProducer extends BaseKafkaProducer<TaskSubmissionEvent> {

    public TaskProducer(KafkaPublishService publishService,
                        ObjectMapper objectMapper) {
        super(publishService, TaskKafkaConfig.STAGING_TOPIC, objectMapper);
    }

    public void publish(TaskSubmissionEvent event) {
        String key = event.getFarmId() != null
                ? event.getFarmId().toString()
                : event.getSubmissionId().toString();
        publish(key, event);
    }
}