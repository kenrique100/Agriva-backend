package com.agriva.feeding.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;

/**
 * Dashboard stats for the ingredient page.
 * <p>
 * Note: currentStockValue sums qty × cost/unit across currencies.
 * Correct for single-currency farms. For multi-currency farms, this is
 * a known approximation — a per-currency breakdown can be added later
 * if needed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngredientStatsResponse {

    /** Number of live ingredients on this farm. */
    private long totalIngredients;

    /** Total stock on hand measured in KG only (L and EACH are excluded). */
    private BigDecimal totalStockKg;

    /** Σ (qty × cost/unit) across all live stock — money tied up in inventory. */
    private BigDecimal currentStockValue;

    /** First non-null currency among the farm's ingredients — for display. */
    private String currency;
}