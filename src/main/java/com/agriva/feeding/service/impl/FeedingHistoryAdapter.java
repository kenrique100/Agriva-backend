package com.agriva.feeding.service.impl;

import com.agriva.feeding.port.ConsumerType;
import com.agriva.feeding.port.FeedingHistoryPort;
import com.agriva.feeding.repository.FeedingEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of {@link FeedingHistoryPort}. Backed by
 * {@link FeedingEventRepository}; read-only, farm-scoped, no locking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedingHistoryAdapter implements FeedingHistoryPort {

    private final FeedingEventRepository feedingEventRepository;

    @Override
    @Transactional(readOnly = true)
    public BigDecimal totalFeedConsumedKg(UUID farmId,
                                          ConsumerType consumerType,
                                          UUID consumerId,
                                          LocalDate from,
                                          LocalDate to) {
        BigDecimal sum = feedingEventRepository.sumQuantityKgInRange(
                farmId, consumerType, consumerId, from, to);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyFeedConsumption> dailyFeedConsumed(UUID farmId,
                                                        ConsumerType consumerType,
                                                        UUID consumerId,
                                                        LocalDate from,
                                                        LocalDate to) {
        return feedingEventRepository.sumQuantityKgByDay(
                        farmId, consumerType, consumerId, from, to)
                .stream()
                .map(row -> new DailyFeedConsumption(
                        (LocalDate) row[0],
                        (BigDecimal) row[1],
                        ((Number) row[2]).intValue()))
                .toList();
    }
}