package com.agriva.shared.kafka;

import com.agriva.shared.exception.KafkaUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Base class for domain producers. The payload is handed to
 * {@link KafkaPublishService} <strong>as-is</strong>; the KafkaTemplate's
 * configured {@code JsonSerializer} performs the single JSON encode.
 */
public abstract class BaseKafkaProducer<T> {

    protected final KafkaPublishService publishService;
    protected final String topic;

    @Deprecated
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
            publishService.publish(topic, key, value);
        } catch (KafkaUnavailableException e) {
            // Propagate so GlobalExceptionHandler maps it to 503.
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to publish Kafka payload for topic " + topic, e);
        }
    }
}