package com.agriva.shared.kafka.topics;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class SalaryKafkaConfig {
    public static final String STAGING_TOPIC = "agriva.salary.staging";
    public static final String STAGING_DLQ_TOPIC = "agriva.salary.staging.dlq";
    public static final String STAGING_CONSUMER_GROUP = "agriva-salary-staging-consumer";

    @Bean public NewTopic salaryStagingTopic() {
        return TopicBuilder.name(STAGING_TOPIC).partitions(3).replicas(1).build();
    }
    @Bean public NewTopic salaryStagingDlqTopic() {
        return TopicBuilder.name(STAGING_DLQ_TOPIC).partitions(1).replicas(1).build();
    }
}