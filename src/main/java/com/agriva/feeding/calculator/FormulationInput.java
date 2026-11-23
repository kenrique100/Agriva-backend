package com.agriva.feeding.calculator;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record FormulationInput(
        UUID farmId,
        FormulationMode mode,
        String targetSpecies,
        BigDecimal targetQuantityKg,
        BigDecimal targetCpValue,
        List<AvailableIngredient> availableIngredients,
        List<ManualLine> manualLines,
        UUID formulaTemplateId
) {}