package com.agriva.feeding.port;

import java.util.Optional;
import java.util.UUID;

/**
 * SPI for cross-service feed consumption.
 * <p>
 * Each consumer domain (Fishery, Poultry, Livestock, …) implements exactly one
 * bean implementing this interface. The feeding module injects
 * {@code List<FeedConsumerPort>} and dispatches by {@link #consumerType()}.
 * <p>
 * Implementations MUST be read-only with respect to their own domain: the
 * feeding module only asks "tell me about consumer X"; it never mutates the
 * consumer's state. Any state changes flow back through the consumer's own
 * services (typically listening to a future FeedingEventCreated domain event).
 */
public interface FeedConsumerPort {

    /** Which consumer type this adapter serves. Must not be null. */
    ConsumerType consumerType();

    /**
     * Snapshot the consumer for a feed event.
     * Returns {@link Optional#empty()} if the consumer does not exist for this
     * farm, has been deleted, or is otherwise inactive. The caller turns that
     * into a 422 ConsumerNotFoundInDomainException.
     */
    Optional<ConsumerSnapshot> snapshot(UUID farmId, UUID consumerId);

    /**
     * Resolve a human-readable display name back to the consumer UUID.
     * <p>
     * Used by the Excel feeding template where users type a name instead of an
     * id. Domains that do not need name resolution may leave the default
     * implementation (returns empty → Excel feeding for that domain requires
     * an explicit consumer id in the upload file).
     */
    default Optional<UUID> resolveConsumerIdByDisplayName(UUID farmId, String displayName) {
        return Optional.empty();
    }
}