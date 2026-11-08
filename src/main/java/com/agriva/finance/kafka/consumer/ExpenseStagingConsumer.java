package com.agriva.finance.kafka.consumer;

import com.agriva.shared.kafka.topics.ExpenseKafkaConfig;
import com.agriva.finance.model.dto.request.ExpenseSubmissionEvent;
import com.agriva.finance.service.ExpenseStagingService;
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
public class ExpenseStagingConsumer {

    private final ExpenseStagingService expenseStagingService;

    @KafkaListener(
            topics = ExpenseKafkaConfig.STAGING_TOPIC,
            groupId = ExpenseKafkaConfig.STAGING_CONSUMER_GROUP,
            containerFactory = "expenseKafkaListenerContainerFactory")
    public void onExpenseSubmitted(
            @Payload ExpenseSubmissionEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received expense submission {} partition={} offset={}",
                event.getSubmissionId(), partition, offset);
        expenseStagingService.persistStaged(event);
    }
}