package com.agriva.feeding.calculator;

import java.math.BigDecimal;
import java.util.UUID;

public record AvailableIngredient(
        UUID ingredientId,
        String name,
        BigDecimal quantityOnHand,
        String unit,
        BigDecimal costPerUnit,
        BigDecimal crudeProteinPct
) {}