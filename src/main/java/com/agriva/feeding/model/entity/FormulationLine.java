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

    @Column(name = "ingredient_name", nullable = false, length = 100)
    private String ingredientName;

    @Column(name = "quantity_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityKg;

    @Column(precision = 7, scale = 4)
    private BigDecimal percentage;

    @Column(name = "unit_cost", precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(name = "line_cost", precision = 19, scale = 4)
    private BigDecimal lineCost;

    @Column(name = "crude_protein_pct", precision = 7, scale = 4)
    private BigDecimal crudeProteinPct;
}