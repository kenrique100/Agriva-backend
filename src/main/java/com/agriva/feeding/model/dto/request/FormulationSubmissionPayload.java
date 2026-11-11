package com.agriva.feeding.model.dto.request;

import com.agriva.feeding.calculator.FormulationMode;

import java.math.BigDecimal;
import java.util.List;

/**
 * Business payload for the formulation bulk stream. Ingredient lines are
 * resolved by NAME in the validator (cross-check against the farm's
 * ingredient catalog). Deferring resolution allows the formulation to be
 * staged before its ingredients exist.
 */
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