package com.agriva.feeding.model.entity;

import com.agriva.shared.models.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "demand_forecast")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemandForecast extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(nullable = false, length = 50)
    private String species;

    @Column(name = "number_of_head", nullable = false)
    private Long numberOfHead;

    @Column(name = "initial_weight_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal initialWeightKg;

    @Column(name = "target_weight_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal targetWeightKg;

    @Column(name = "water_temperature_c", precision = 6, scale = 2)
    private BigDecimal waterTemperatureC;

    @Column(name = "target_cp_value", precision = 6, scale = 2)
    private BigDecimal targetCpValue;

    @Column(name = "formula_template_id")
    private UUID formulaTemplateId;

    @Column(name = "total_feed_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalFeedKg;

    @Column(name = "total_days", nullable = false)
    private Integer totalDays;

    @Column(name = "average_daily_feed_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal averageDailyFeedKg;

    @Column(name = "algorithm_version", nullable = false, length = 50)
    private String algorithmVersion;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "computed_cp_value", precision = 6, scale = 2)
    private BigDecimal computedCpValue;

    @Column(name = "nutrient_summary_json", columnDefinition = "TEXT")
    private String nutrientSummaryJson;

    @Version
    @Column(nullable = false)
    private Long version;
}