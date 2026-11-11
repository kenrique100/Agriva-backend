package com.agriva.feeding.calculator;

import java.math.BigDecimal;

public record GrowthStageResult(
        String stageName,
        BigDecimal startWeightKg,
        BigDecimal endWeightKg,
        int daysInStage,
        BigDecimal feedConsumedKg,
        BigDecimal dailyFeedRequirementKg,
        int feedingTimesPerDay
) {}