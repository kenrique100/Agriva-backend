package com.agriva.feeding.model.dto.request;

import com.agriva.feeding.calculator.FormulationMode;

import java.math.BigDecimal;
import java.util.List;

public record FormulationSubmissionPayload(
        String name,
        String targetSpecies,
        FormulationMode mode,
        BigDecimal targetQuantityKg,
        BigDecimal targetCpValue,
        List<Line> lines
) {
    public record Line(String ingredientName, BigDecimal quantityKg) {}
}