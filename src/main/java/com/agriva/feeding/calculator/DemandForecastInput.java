package com.agriva.feeding.calculator;

import java.math.BigDecimal;
import java.util.UUID;

public record DemandForecastInput(
        UUID farmId,
        String species,
        int numberOfHead,
        BigDecimal initialWeightKg,
        BigDecimal targetWeightKg,
        BigDecimal waterTemperatureC,
        BigDecimal targetCpValue,
        UUID formulaTemplateId
) {}