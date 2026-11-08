package com.agriva.shared.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    /**
     * The single source of truth for bootstrap servers.
     * <p>
     * In tests, {@code @ServiceConnection} publishes a
     * KafkaConnectionDetails bean whose getBootstrapServers() is the
     * Testcontainers URL (e.g. localhost:32471).
     * <p>
     * In production, Spring Boot's KafkaAutoConfiguration publishes a
     * PropertiesKafkaConnectionDetails bean whose getBootstrapServers()
     * comes from spring.kafka.bootstrap-servers.
     * <p>
     * Using this bean — instead of KafkaProperties — is correct in both
     * environments.
     */
    private final KafkaConnectionDetails kafkaConnectionDetails;

    public KafkaProducerConfig(KafkaConnectionDetails kafkaConnectionDetails) {
        this.kafkaConnectionDetails = kafkaConnectionDetails;
    }

    @Bean
    @Primary
    public ProducerFactory<String, Object> producerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        // Never use the transactional.id — this template is plain fire-and-forget.
        props.remove(ProducerConfig.TRANSACTIONAL_ID_CONFIG);
        // Override bootstrap.servers with the container/properties source-of-truth.
        List<String> bootstrap = kafkaConnectionDetails.getBootstrapServers();
        if (bootstrap != null && !bootstrap.isEmpty()) {
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        }
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    @Primary
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ProducerFactory<Object, Object> modulithProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties(null));
        props.remove(ProducerConfig.TRANSACTIONAL_ID_CONFIG);
        List<String> bootstrap = kafkaConnectionDetails.getBootstrapServers();
        if (bootstrap != null && !bootstrap.isEmpty()) {
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        }
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<Object, Object> modulithKafkaTemplate(ProducerFactory<Object, Object> modulithProducerFactory) {
        return new KafkaTemplate<>(modulithProducerFactory);
    }
}