package com.agriva.feeding.mapper;

import com.agriva.feeding.model.dto.response.IngredientResponse;
import com.agriva.feeding.model.entity.Ingredient;
import com.agriva.feeding.model.entity.IngredientStock;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class IngredientMapper {

    public IngredientResponse toResponse(Ingredient i, IngredientStock stock) {
        if (i == null) return null;

        IngredientResponse.CurrentStock currentStock = null;
        BigDecimal totalCost = null;

        if (stock != null) {
            currentStock = IngredientResponse.CurrentStock.builder()
                    .quantityOnHand(stock.getQuantityOnHand())
                    .unit(stock.getUnit() != null ? stock.getUnit() : i.getUnit())
                    .lastReplenishedAt(stock.getLastReplenishedAt())
                    .build();

            if (stock.getQuantityOnHand() != null && i.getCostPerUnit() != null) {
                totalCost = stock.getQuantityOnHand()
                        .multiply(i.getCostPerUnit())
                        .setScale(4, RoundingMode.HALF_UP);
            }
        }

        return IngredientResponse.builder()
                .id(i.getId())
                .farmId(i.getFarmId())
                .name(i.getName())
                .unit(i.getUnit())
                .costPerUnit(i.getCostPerUnit())
                .currency(i.getCurrency())
                .supplier(i.getSupplier())
                .densityKgPerL(i.getDensityKgPerL())
                .crudeProteinPct(i.getCrudeProteinPct())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .currentStock(currentStock)
                .totalCost(totalCost)
                .build();
    }
}