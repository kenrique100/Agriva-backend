package com.agriva.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class IncomeKafkaConfig {

    public static final String STAGING_TOPIC = "agriva.income.staging";
    public static final String STAGING_DLQ_TOPIC = "agriva.income.staging.dlq";
    public static final String STAGING_CONSUMER_GROUP = "agriva-income-staging-consumer";

    @Bean
    public NewTopic incomeStagingTopic() {
        return TopicBuilder.name(STAGING_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic incomeStagingDlqTopic() {
        return TopicBuilder.name(STAGING_DLQ_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}