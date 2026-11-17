package com.agriva.feeding.mapper;

import com.agriva.feeding.model.dto.response.IngredientResponse;
import com.agriva.feeding.model.entity.Ingredient;
import com.agriva.feeding.model.entity.IngredientStock;
import org.springframework.stereotype.Component;

@Component
public class IngredientMapper {

    public IngredientResponse toResponse(Ingredient ingredient, IngredientStock stock) {
        IngredientResponse.CurrentStock cs = null;
        if (stock != null) {
            cs = IngredientResponse.CurrentStock.builder()
                    .quantityOnHand(stock.getQuantityOnHand())
                    .unit(stock.getUnit())
                    .lastReplenishedAt(stock.getLastReplenishedAt())
                    .build();
        }
        return IngredientResponse.builder()
                .id(ingredient.getId())
                .farmId(ingredient.getFarmId())
                .name(ingredient.getName())
                .unit(ingredient.getUnit())
                .costPerUnit(ingredient.getCostPerUnit())
                .currency(ingredient.getCurrency())
                .supplier(ingredient.getSupplier())
                .supplier(ingredient.getSupplier())
                .densityKgPerL(ingredient.getDensityKgPerL())
                .createdAt(ingredient.getCreatedAt())
                .updatedAt(ingredient.getUpdatedAt())
                .currentStock(cs)
                .build();
    }
}