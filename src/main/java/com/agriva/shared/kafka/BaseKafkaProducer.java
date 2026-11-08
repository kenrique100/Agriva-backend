package com.agriva.shared.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;

public abstract class BaseKafkaProducer<T> {

    protected final KafkaTemplate<String, Object> kafkaTemplate;
    protected final String topic;
    protected final ObjectMapper objectMapper;

    protected BaseKafkaProducer(KafkaTemplate<String, Object> kafkaTemplate,
                                String topic,
                                ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.objectMapper = objectMapper;
    }

    /**
     * The template is wired with ByteArraySerializer, so serialize to JSON
     * bytes here. Consumers use {@code JsonDeserializer} with the concrete
     * event type (see {@code *.kafka.consumer.*StagingConsumer}), so bytes
     * on the wire + a typed listener is the correct pairing.
     */
    public void publish(String key, T value) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(value);
            kafkaTemplate.send(topic, key, payload);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to serialize Kafka payload for topic " + topic, e);
        }
    }
}