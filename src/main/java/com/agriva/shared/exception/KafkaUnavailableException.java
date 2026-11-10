package com.agriva.shared.exception;

/**
 * Raised by {@code KafkaPublishService} when the circuit breaker is open or
 * a publishing attempt fails. Maps to HTTP 503 (with {@code Retry-After: 30})
 * in {@code GlobalExceptionHandler}.
 */
public class KafkaUnavailableException extends RuntimeException {

    public KafkaUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}