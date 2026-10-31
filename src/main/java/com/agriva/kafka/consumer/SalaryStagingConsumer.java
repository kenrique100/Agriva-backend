package com.agriva.kafka.consumer;

import com.agriva.config.SalaryKafkaConfig;
import com.agriva.model.dto.salary.SalarySubmissionEvent;
import com.agriva.service.SalaryStagingService;
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
public class SalaryStagingConsumer {

    private final SalaryStagingService salaryStagingService;

    @KafkaListener(
            topics = SalaryKafkaConfig.STAGING_TOPIC,
            groupId = SalaryKafkaConfig.STAGING_CONSUMER_GROUP,
            containerFactory = "salaryKafkaListenerContainerFactory")
    public void onSalarySubmitted(
            @Payload SalarySubmissionEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received salary submission {} partition={} offset={}",
                event.getSubmissionId(), partition, offset);
        salaryStagingService.persistStaged(event);
    }
}