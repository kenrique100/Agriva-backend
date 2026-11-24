package com.agriva.feeding.model.entity;

import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.feeding.model.enums.StockAdjustmentType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ingredient_stock_adjustment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientStockAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "ingredient_id", nullable = false)
    private UUID ingredientId;

    @Column(name = "previous_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal previousQuantity;

    @Column(name = "new_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal newQuantity;

    @Column(name = "delta", nullable = false, precision = 19, scale = 4)
    private BigDecimal delta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IngredientUnit unit;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 20)
    private StockAdjustmentType adjustmentType;

    @Column(name = "performed_by_user_id")
    private UUID performedByUserId;

    @Column(name = "performed_at", nullable = false, updatable = false)
    private LocalDateTime performedAt;

    @PrePersist
    void onCreate() {
        if (performedAt == null) performedAt = LocalDateTime.now();
    }
}