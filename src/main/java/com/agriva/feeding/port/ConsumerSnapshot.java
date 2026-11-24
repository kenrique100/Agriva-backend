package com.agriva.feeding.port;

import java.util.UUID;

public record ConsumerSnapshot(
        UUID consumerId,
        String displayName,
        long headcount,
        String stage
) {
    public ConsumerSnapshot {
        if (consumerId == null) throw new IllegalArgumentException("consumerId is required");
        if (displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("displayName is required");
        if (headcount < 0) throw new IllegalArgumentException("headcount must be >= 0");
    }
}