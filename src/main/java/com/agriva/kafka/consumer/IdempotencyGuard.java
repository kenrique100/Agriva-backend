package com.agriva.kafka.consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Atomically records "this event was processed by this consumer".
 * Returns false if the event was already processed (duplicate delivery).
 */
@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

    private final JdbcTemplate jdbc;

    public boolean tryMarkProcessed(UUID eventId, String consumerGroup) {
        try {
            jdbc.update(
                    "INSERT INTO consumer_processed_events (event_id, consumer_group) VALUES (?, ?)",
                    eventId, consumerGroup);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
    }
}