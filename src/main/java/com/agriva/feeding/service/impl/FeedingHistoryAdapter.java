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
                        toLocalDate(row[0]),
                        toBigDecimal(row[1]),
                        toInt(row[2])))
                .toList();
    }

    private static LocalDate toLocalDate(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDate ld) return ld;
        if (o instanceof java.sql.Date sd) return sd.toLocalDate();
        if (o instanceof java.util.Date ud) return new java.sql.Date(ud.getTime()).toLocalDate();
        throw new IllegalStateException(
                "Cannot convert value of type " + o.getClass().getName() + " to LocalDate");
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal bd) return bd;
        if (o instanceof Number n) return new BigDecimal(n.toString());
        throw new IllegalStateException(
                "Cannot convert value of type " + o.getClass().getName() + " to BigDecimal");
    }

    private static int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        throw new IllegalStateException(
                "Cannot convert value of type " + o.getClass().getName() + " to int");
    }
}