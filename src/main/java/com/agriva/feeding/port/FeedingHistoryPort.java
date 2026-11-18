package com.agriva.feeding.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FeedingHistoryPort {

    BigDecimal totalFeedConsumedKg(UUID farmId,
                                   ConsumerType consumerType,
                                   UUID consumerId,
                                   LocalDate from,
                                   LocalDate to);

    List<DailyFeedConsumption> dailyFeedConsumed(UUID farmId,
                                                 ConsumerType consumerType,
                                                 UUID consumerId,
                                                 LocalDate from,
                                                 LocalDate to);

    record DailyFeedConsumption(LocalDate date, BigDecimal quantityKg, int feedingCount) {}
}