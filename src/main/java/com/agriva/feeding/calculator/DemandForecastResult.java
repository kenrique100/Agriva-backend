package com.agriva.feeding.calculator;

import java.math.BigDecimal;
import java.util.List;

public record DemandForecastResult(
        List<GrowthStageResult> stages,
        int totalDays,
        BigDecimal totalFeedKg,
        BigDecimal averageDailyFeedKg,
        String algorithmVersion
) {}