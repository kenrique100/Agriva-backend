package com.agriva;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:17.4-alpine")
                .withDatabaseName("agriva_test")
                .withUsername("agriva")
                .withPassword("agriva")
                .withReuse(true);
    }

    @Bean
    @ServiceConnection
    public KafkaContainer kafkaContainer() {
        // Kafka 3.9.0 rejects the image's default advertised listener (0.0.0.0),
        // so we stay on 3.8.0 and let Testcontainers configure listeners itself.
        // Do NOT pass .withEnv("KAFKA_LISTENERS", …) here.
        return new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"))
                .withReuse(true);
    }

    @Bean
    @ServiceConnection(name = "redis")
    public GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:8.0-alpine"))
                .withExposedPorts(6379)
                .withReuse(true);
    }

    /**
     * Belt-and-suspenders bootstrap.
     *
     * {@code @ServiceConnection} publishes a KafkaConnectionDetails bean, which
     * Spring Boot's *autoconfigured* ProducerFactory / ConsumerFactory consume.
     *
     * Any user-defined {@code ProducerFactory} / {@code ConsumerFactory} bean
     * that reads from {@code KafkaProperties} (i.e. spring.kafka.*) will NOT
     * see that value — KafkaProperties is bound from the Environment, not from
     * KafkaConnectionDetails. This registrar bridges the gap by exposing the
     * container's bootstrap URL as spring.kafka.bootstrap-servers before any
     * bean creation.
     */
    @Bean
    public DynamicPropertyRegistrar kafkaBootstrapServersRegistrar(KafkaContainer kafkaContainer) {
        return registry -> registry.add(
                "spring.kafka.bootstrap-servers",
                kafkaContainer::getBootstrapServers);
    }
}