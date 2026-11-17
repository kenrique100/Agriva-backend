package com.agriva.shared.kafka.topics;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class FeedFormulationKafkaConfig {

    public static final String STAGING_TOPIC = "agriva.feeding.formulation.staging";
    public static final String STAGING_DLQ_TOPIC = "agriva.feeding.formulation.staging.dlq";
    public static final String STAGING_CONSUMER_GROUP = "agriva-feeding-formulation-staging-consumer";

    @Bean
    public NewTopic feedFormulationStagingTopic() {
        return TopicBuilder.name(STAGING_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic feedFormulationStagingDlqTopic() {
        return TopicBuilder.name(STAGING_DLQ_TOPIC).partitions(1).replicas(1).build();
    }
}