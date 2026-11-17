package com.agriva.feeding.port;

/**
 * The species/domain a consumer belongs to.
 * Every consumer domain MUST register exactly one {@link FeedConsumerPort}
 * whose {@code consumerType()} returns its value from this enum.
 */
public enum ConsumerType {
    FISHERY,
    POULTRY,
    LIVESTOCK,
    OTHER
}