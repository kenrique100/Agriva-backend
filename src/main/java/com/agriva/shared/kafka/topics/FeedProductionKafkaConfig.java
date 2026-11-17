package com.agriva.shared.kafka.topics;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class FeedProductionKafkaConfig {

    public static final String STAGING_TOPIC = "agriva.feeding.production.staging";
    public static final String STAGING_DLQ_TOPIC = "agriva.feeding.production.staging.dlq";
    public static final String STAGING_CONSUMER_GROUP = "agriva-feeding-production-staging-consumer";

    @Bean
    public NewTopic feedProductionStagingTopic() {
        return TopicBuilder.name(STAGING_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic feedProductionStagingDlqTopic() {
        return TopicBuilder.name(STAGING_DLQ_TOPIC).partitions(1).replicas(1).build();
    }
}