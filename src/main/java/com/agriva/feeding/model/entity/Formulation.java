package com.agriva.feeding.model.entity;

import com.agriva.feeding.calculator.FormulationMode;
import com.agriva.shared.models.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "formulation")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Formulation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "target_species", nullable = false, length = 50)
    private String targetSpecies;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FormulationMode mode;

    @Column(name = "target_quantity_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal targetQuantityKg;

    @Column(name = "target_cp_value", precision = 6, scale = 2)
    private BigDecimal targetCpValue;

    @Column(name = "formula_template_id")
    private UUID formulaTemplateId;

    @Column(name = "total_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalKg;

    @Column(name = "total_cost", precision = 19, scale = 4)
    private BigDecimal totalCost;

    @Column(name = "algorithm_version", nullable = false, length = 50)
    private String algorithmVersion;

    @Column(name = "nutrient_summary_json", columnDefinition = "TEXT")
    private String nutrientSummaryJson;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "expense_submission_id")
    private UUID expenseSubmissionId;

    @Column(name = "computed_cp_value", precision = 6, scale = 2)
    private BigDecimal computedCpValue;

    @Version
    @Column(nullable = false)
    private Long version;
}