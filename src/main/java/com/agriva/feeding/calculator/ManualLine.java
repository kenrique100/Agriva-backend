package com.agriva.feeding.calculator;

import java.math.BigDecimal;
import java.util.UUID;

public record ManualLine(
        UUID ingredientId,
        BigDecimal quantityKg
) {}