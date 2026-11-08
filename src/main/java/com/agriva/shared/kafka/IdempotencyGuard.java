package com.agriva.shared.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

    private final JdbcTemplate jdbc;

    public boolean tryMarkProcessed(UUID eventId, String consumerGroup) {
        int rows = jdbc.update(
                "INSERT INTO consumer_processed_events (event_id, consumer_group) " +
                        "VALUES (?, ?) ON CONFLICT (event_id, consumer_group) DO NOTHING",
                eventId, consumerGroup);
        return rows > 0;
    }
}