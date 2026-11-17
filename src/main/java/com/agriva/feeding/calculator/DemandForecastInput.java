package com.agriva.feeding.calculator;

import java.math.BigDecimal;
import java.util.UUID;

public record DemandForecastInput(
        UUID farmId,
        String species,
        long numberOfHead,
        BigDecimal initialWeightKg,
        BigDecimal targetWeightKg,
        BigDecimal waterTemperatureC,   // nullable for non-aquatic species
        BigDecimal targetCpValue,       // nullable
        UUID formulaTemplateId          // nullable; user's calculator decides whether to use it
) {}