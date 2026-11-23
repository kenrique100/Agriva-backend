package com.agriva.feeding.calculator;

import java.math.BigDecimal;
import java.util.UUID;

public record FormulationLineResult(
        UUID ingredientId,
        BigDecimal quantityInStockUnit,
        BigDecimal percentage,
        BigDecimal unitCost
) {}