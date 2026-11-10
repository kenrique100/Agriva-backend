package com.agriva.shared.kafka.topics;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class CropKafkaConfig {

    public static final String STAGING_TOPIC = "agriva.crop.staging";
    public static final String STAGING_DLQ_TOPIC = "agriva.crop.staging.dlq";
    public static final String STAGING_CONSUMER_GROUP = "agriva-crop-staging-consumer";

    @Bean
    public NewTopic cropStagingTopic() {
        return TopicBuilder.name(STAGING_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic cropStagingDlqTopic() {
        return TopicBuilder.name(STAGING_DLQ_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}