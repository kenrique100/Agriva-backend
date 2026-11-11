package com.agriva.feeding.calculator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record FormulationResult(
        List<FormulationLineResult> lines,
        BigDecimal totalKg,
        BigDecimal totalCost,
        Map<String, BigDecimal> nutrientSummary,
        String algorithmVersion
) {}