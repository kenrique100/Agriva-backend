package com.agriva.feeding.port;

import java.util.UUID;

/**
 * Immutable snapshot of a consumer at feeding time.
 * The feeding module snapshots this onto each FeedingEvent so historical
 * reports are accurate even if the consumer is renamed or restocked later.
 */
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