package com.agriva.shared.kafka;

import com.agriva.shared.exception.KafkaUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Central Kafka publish path, protected by the "kafkaProducer" circuit
 * breaker declared in application.properties.
 * <p>
 * The template itself is synchronous up to the point where the underlying
 * {@code KafkaProducer} is constructed — i.e. when the broker is
 * unreachable the call to {@link KafkaTemplate#send} blocks for up to
 * {@code max.block.ms} (default 60s) before throwing. Without a circuit
 * breaker that timeout is paid on every message and every producer
 * thread, which is exactly the cascade we want to avoid.
 * <p>
 * When the breaker opens, subsequent calls fail immediately with
 * {@link KafkaUnavailableException} — the caller sees a clean 503
 * instead of an exhausted thread pool.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaPublishService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "publishFallback")
    public void publish(String topic, String key, byte[] payload) {
        kafkaTemplate.send(topic, key, payload);
    }

    /**
     * Called by resilience when the circuit is open or the guarded call
     * throws. We deliberately re-throw so the caller knows the message was
     * NOT queued — silently swallowing it would let the API return
     * "QUEUED" while the broker was down.
     */
    @SuppressWarnings("unused")
    private void publishFallback(String topic, String key, byte[] payload, Throwable t) {
        log.error("Kafka circuit OPEN — rejected publish to {} key={}: {}",
                topic, key, t.getMessage());
        throw new KafkaUnavailableException(
                "Kafka is currently unavailable, please retry later", t);
    }
}