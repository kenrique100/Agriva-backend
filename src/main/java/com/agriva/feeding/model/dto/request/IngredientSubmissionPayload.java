package com.agriva.feeding.model.dto.request;

import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.shared.models.enums.Currency;

import java.math.BigDecimal;

public record IngredientSubmissionPayload(
        String name,
        IngredientUnit unit,
        BigDecimal quantity,
        BigDecimal costPerUnit,
        Currency currency,
        String supplier,
        BigDecimal densityKgPerL,
        BigDecimal crudeProteinPct
) {}