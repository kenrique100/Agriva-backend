package com.agriva.feeding.model.entity;

import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.shared.models.entity.AuditableEntity;
import com.agriva.shared.models.enums.Currency;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "ingredient")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ingredient extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IngredientUnit unit;

    @Column(name = "cost_per_unit", precision = 19, scale = 4)
    private BigDecimal costPerUnit;

    @Enumerated(EnumType.STRING)
    @Column(length = 3)
    private Currency currency;

    @Column(length = 200)
    private String supplier;

    @Column(name = "density_kg_per_l", precision = 6, scale = 4)
    @DecimalMin(value = "0.0001", message = "Density must be positive")
    private BigDecimal densityKgPerL;

    @Column(name = "crude_protein_pct", precision = 7, scale = 4)
    @DecimalMin(value = "0", message = "Crude protein cannot be negative")
    private BigDecimal crudeProteinPct;

    @Version
    @Column(nullable = false)
    private Long version;
}