package com.agriva.feeding.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "formulation_template_line")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormulationTemplateLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "ingredient_name", nullable = false, length = 100)
    private String ingredientName;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal percentage;

    @Column(name = "crude_protein_pct", precision = 7, scale = 4)
    private BigDecimal crudeProteinPct;
}