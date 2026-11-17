package com.agriva.feeding.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "formulation_line")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormulationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "formulation_id", nullable = false)
    private UUID formulationId;

    @Column(name = "ingredient_id", nullable = false)
    private UUID ingredientId;

    /** Snapshot of the ingredient name at formulation time (immutable). */
    @Column(name = "ingredient_name", nullable = false, length = 100)
    private String ingredientName;

    /** Quantity in the ingredient's stock unit (KG or L). */
    @Column(name = "quantity_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityKg;

    @Column(precision = 6, scale = 4)
    private BigDecimal percentage;

    @Column(name = "unit_cost", precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "line_cost", precision = 19, scale = 4)
    private BigDecimal lineCost;
}