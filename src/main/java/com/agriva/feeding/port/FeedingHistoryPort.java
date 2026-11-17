package com.agriva.feeding.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only query surface over {@code FeedingEvent}s. Exported from
 * {@code feeding :: port} so downstream consumer domains (Poultry, Livestock)
 * can compute derived metrics (FCR, feed-conversion cost, …) **without**
 * duplicating the farm feed store balance or reaching into feeding internals.
 * <p>
 * Every method returns only rows that already exist; nothing is mutated.
 * Results are aggregated server-side and never materialized into another
 * module's tables.
 */
public interface FeedingHistoryPort {

    /**
     * Total feed delivered (kg) to one consumer in {@code [from, to]}
     * (inclusive on both ends). Returns {@link BigDecimal#ZERO} when no
     * feedings exist in the window — never {@code null}.
     */
    BigDecimal totalFeedConsumedKg(UUID farmId,
                                   ConsumerType consumerType,
                                   UUID consumerId,
                                   LocalDate from,
                                   LocalDate to);

    /**
     * Per-day breakdown of feed consumption, ordered ascending by date.
     * Empty when no feedings exist. Each entry carries a feeding count so
     * callers can derive average feed-per-delivery if desired.
     */
    List<DailyFeedConsumption> dailyFeedConsumed(UUID farmId,
                                                 ConsumerType consumerType,
                                                 UUID consumerId,
                                                 LocalDate from,
                                                 LocalDate to);

    record DailyFeedConsumption(LocalDate date, BigDecimal quantityKg, int feedingCount) {}
}