package com.agriva.shared.kafka;

import com.agriva.shared.exception.KafkaUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Base class for domain producers. Payloads are serialized to JSON bytes
 * here (the template is wired with ByteArraySerializer) and handed to
 * {@link KafkaPublishService}, which owns the circuit breaker.
 */
public abstract class BaseKafkaProducer<T> {

    protected final KafkaPublishService publishService;
    protected final String topic;
    protected final ObjectMapper objectMapper;

    protected BaseKafkaProducer(KafkaPublishService publishService,
                                String topic,
                                ObjectMapper objectMapper) {
        this.publishService = publishService;
        this.topic = topic;
        this.objectMapper = objectMapper;
    }

    public void publish(String key, T value) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(value);
            publishService.publish(topic, key, payload);
        } catch (KafkaUnavailableException e) {
            // Propagate unchanged so GlobalExceptionHandler maps it to 503.
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to serialize/publish Kafka payload for topic " + topic, e);
        }
    }
}