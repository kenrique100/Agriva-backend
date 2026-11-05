package com.agriva.shared.kafka;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

/**
 * Shared producer that removes duplication (R2) and adds circuit breaking +
 * non-blocking async send (fixes L1).
 * <p>
 * Callers get back a CompletableFuture they may ignore (fire-and-forget) or
 * await if they want to wait for Kafka ack. The API thread is never blocked
 * on Kafka.
 */
@Slf4j
public abstract class BaseKafkaProducer<E> {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    protected BaseKafkaProducer(KafkaTemplate<String, Object> kafkaTemplate, String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @CircuitBreaker(name = "kafkaProducer", fallbackMethod = "publishFallback")
    public CompletableFuture<SendResult<String, Object>> publish(String key, E event) {
        CompletableFuture<SendResult<String, Object>> f =
                kafkaTemplate.send(topic, key, event);
        f.whenComplete((r, ex) -> {
            if (ex != null) {
                log.error("Kafka publish failed topic={} key={}: {}", topic, key, ex.getMessage());
            } else if (r != null) {
                log.debug("Published to {} partition={} offset={}",
                        topic, r.getRecordMetadata().partition(), r.getRecordMetadata().offset());
            }
        });
        return f;
    }

    /**
     * Circuit breaker fallback: called when the breaker is OPEN, i.e. Kafka is
     * considered down. We swallow the exception and return a failed future so
     * the caller (HTTP request) still succeeds — the submission is accepted
     * by the API but the message is dropped. In a stricter setup, write to an
     * outbox table here instead.
     */
    @SuppressWarnings("unused")
    private CompletableFuture<SendResult<String, Object>> publishFallback(
            String key, E event, Throwable t) {
        log.warn("Kafka circuit open — dropping event key={} cause={}", key, t.toString());
        return CompletableFuture.failedFuture(t);
    }
}