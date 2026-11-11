package com.agriva.feeding.model.entity;

import com.agriva.feeding.model.enums.IngredientUnit;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ingredient_stock",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_ingredient_stock_farm_ingredient",
                columnNames = {"farm_id", "ingredient_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientStock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "ingredient_id", nullable = false)
    private UUID ingredientId;

    @Column(name = "quantity_on_hand", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal quantityOnHand = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IngredientUnit unit;

    @Column(name = "last_replenished_at")
    private LocalDateTime lastReplenishedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}