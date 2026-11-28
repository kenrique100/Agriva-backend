package com.agriva.shared.kafka;

import com.agriva.shared.exception.KafkaUnavailableException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaPublishService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "publishFallback")
    public void publish(String topic, String key, Object payload) {
        byte[] value;
        try {
            value = objectMapper.writeValueAsBytes(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Failed to serialize Kafka payload for topic " + topic, e);
        }
        kafkaTemplate.send(topic, key, value);
    }

    @SuppressWarnings("unused")
    private void publishFallback(String topic, String key, Object payload, Throwable t) {
        log.error("Kafka circuit OPEN — rejected publish to {} key={}: {}",
                topic, key, t.getMessage());
        throw new KafkaUnavailableException(
                "Kafka is currently unavailable, please retry later", t);
    }
}