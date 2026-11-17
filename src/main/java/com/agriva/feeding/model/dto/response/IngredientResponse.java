package com.agriva.feeding.model.dto.response;

import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.shared.models.enums.Currency;
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
public class IngredientResponse {

    private UUID id;
    private UUID farmId;
    private String name;
    private IngredientUnit unit;
    private BigDecimal costPerUnit;
    private Currency currency;
    private String supplier;
    private BigDecimal densityKgPerL;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private CurrentStock currentStock;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentStock {
        private BigDecimal quantityOnHand;
        private IngredientUnit unit;
        private LocalDateTime lastReplenishedAt;
    }
}