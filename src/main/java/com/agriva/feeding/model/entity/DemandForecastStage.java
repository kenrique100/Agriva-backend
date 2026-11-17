package com.agriva.feeding.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "demand_forecast_stage",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_dfs_forecast_index",
                columnNames = {"forecast_id", "stage_index"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemandForecastStage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "forecast_id", nullable = false)
    private UUID forecastId;

    @Column(name = "stage_index", nullable = false)
    private Integer stageIndex;

    @Column(name = "stage_name", nullable = false, length = 30)
    private String stageName;

    @Column(name = "start_weight_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal startWeightKg;

    @Column(name = "end_weight_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal endWeightKg;

    @Column(name = "days_in_stage", nullable = false)
    private Integer daysInStage;

    @Column(name = "feed_consumed_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal feedConsumedKg;

    @Column(name = "daily_feed_requirement_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal dailyFeedRequirementKg;

    @Column(name = "feeding_times_per_day", nullable = false)
    private Integer feedingTimesPerDay;
}