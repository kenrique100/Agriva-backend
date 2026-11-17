package com.agriva.shared.kafka.topics;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class FeedIngredientKafkaConfig {

    public static final String STAGING_TOPIC = "agriva.feeding.ingredient.staging";
    public static final String STAGING_DLQ_TOPIC = "agriva.feeding.ingredient.staging.dlq";
    public static final String STAGING_CONSUMER_GROUP = "agriva-feeding-ingredient-staging-consumer";

    @Bean
    public NewTopic feedIngredientStagingTopic() {
        return TopicBuilder.name(STAGING_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic feedIngredientStagingDlqTopic() {
        return TopicBuilder.name(STAGING_DLQ_TOPIC).partitions(1).replicas(1).build();
    }
}