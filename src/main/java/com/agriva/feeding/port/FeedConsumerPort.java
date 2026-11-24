package com.agriva.feeding.port;

import java.util.Optional;
import java.util.UUID;

public interface FeedConsumerPort {

    /** Which consumer type this adapter serves. Must not be null. */
    ConsumerType consumerType();
    Optional<ConsumerSnapshot> snapshot(UUID farmId, UUID consumerId);
    default Optional<UUID> resolveConsumerIdByDisplayName(UUID farmId, String displayName) {
        return Optional.empty();
    }
}