package com.agriva.shared.exception;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * Thrown when a formulation/production requires more of an ingredient than
 * is currently in stock. Maps to HTTP 422 in GlobalExceptionHandler.
 */
@Getter
public class InsufficientIngredientQuantityException extends RuntimeException {

    private final String ingredientName;
    private final BigDecimal required;
    private final BigDecimal available;
    private final String unit;

    public InsufficientIngredientQuantityException(String ingredientName,
                                                   BigDecimal required,
                                                   BigDecimal available,
                                                   String unit) {
        super(String.format(
                "Insufficient ingredient '%s': required %s %s, available %s %s",
                ingredientName,
                required != null ? required.toPlainString() : "0",
                unit,
                available != null ? available.toPlainString() : "0",
                unit));
        this.ingredientName = ingredientName;
        this.required = required;
        this.available = available;
        this.unit = unit;
    }

}