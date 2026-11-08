package com.agriva.task.kafka.consumer;

import com.agriva.shared.kafka.topics.TaskKafkaConfig;
import com.agriva.task.model.dto.requests.TaskSubmissionEvent;
import com.agriva.task.service.TaskStagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskStagingConsumer {

    private final TaskStagingService taskStagingService;

    @KafkaListener(
            topics = TaskKafkaConfig.STAGING_TOPIC,
            groupId = TaskKafkaConfig.STAGING_CONSUMER_GROUP,
            containerFactory = "taskKafkaListenerContainerFactory")
    public void onTaskSubmitted(
            @Payload TaskSubmissionEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received task submission {} partition={} offset={}",
                event.getSubmissionId(), partition, offset);
        taskStagingService.persistStaged(event);
    }
}