package com.agriva.feeding.mapper;

import com.agriva.feeding.model.dto.response.IngredientResponse;
import com.agriva.feeding.model.entity.Ingredient;
import com.agriva.feeding.model.entity.IngredientStock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@Slf4j
public class IngredientMapper {

    public IngredientResponse toResponse(Ingredient i, IngredientStock stock) {
        if (i == null) return null;

        IngredientResponse.CurrentStock currentStock = null;
        BigDecimal totalCost = null;

        if (stock != null) {
            if (stock.getUnit() != null && stock.getUnit() != i.getUnit()) {
                log.warn("Stock unit {} for ingredient {} ('{}') disagrees with "
                                + "ingredient unit {} — returning the ingredient unit in the response",
                        stock.getUnit(), i.getId(), i.getName(), i.getUnit());
            }

            currentStock = IngredientResponse.CurrentStock.builder()
                    .quantityOnHand(stock.getQuantityOnHand())
                    .unit(i.getUnit())
                    .lastReplenishedAt(stock.getLastReplenishedAt())
                    .version(stock.getVersion())
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