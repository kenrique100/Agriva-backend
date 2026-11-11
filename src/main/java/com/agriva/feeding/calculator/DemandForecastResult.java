package com.agriva.feeding.calculator;

import java.math.BigDecimal;
import java.util.List;

public record DemandForecastResult(
        List<GrowthStageResult> stages,
        BigDecimal totalFeedKg,
        int totalDays,
        BigDecimal averageDailyFeedKg,
        String algorithmVersion
) {}