package com.agriva.kafka.consumer;

import com.agriva.config.IncomeKafkaConfig;
import com.agriva.model.dto.income.IncomeSubmissionEvent;
import com.agriva.service.IncomeStagingService;
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
public class IncomeStagingConsumer {

    private final IncomeStagingService incomeStagingService;

    @KafkaListener(
            topics = IncomeKafkaConfig.STAGING_TOPIC,
            groupId = IncomeKafkaConfig.STAGING_CONSUMER_GROUP,
            containerFactory = "incomeKafkaListenerContainerFactory")
    public void onIncomeSubmitted(
            @Payload IncomeSubmissionEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received income submission {} partition={} offset={}",
                event.getSubmissionId(), partition, offset);

        incomeStagingService.persistStaged(event);
    }
}