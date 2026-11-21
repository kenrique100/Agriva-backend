package com.agriva.feeding.model.dto.request;

import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.shared.models.enums.Currency;

import java.math.BigDecimal;

/**
 * Business payload for the ingredient bulk stream: creates the master + an
 * initial stock replenishment in one shot.
 * <p>
 * crudeProteinPct is not nullable
 * without it deserialize cleanly.
 */
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