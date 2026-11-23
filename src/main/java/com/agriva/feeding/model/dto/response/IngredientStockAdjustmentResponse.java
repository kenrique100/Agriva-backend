package com.agriva.feeding.model.dto.response;

import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.feeding.model.enums.StockAdjustmentType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngredientStockAdjustmentResponse {
    private UUID id;
    private UUID farmId;
    private UUID ingredientId;
    private BigDecimal previousQuantity;
    private BigDecimal newQuantity;
    private BigDecimal delta;
    private IngredientUnit unit;
    private String reason;
    private StockAdjustmentType adjustmentType;
    private UUID performedByUserId;
    private LocalDateTime performedAt;
}