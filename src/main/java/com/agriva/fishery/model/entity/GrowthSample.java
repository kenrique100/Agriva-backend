package com.agriva.fishery.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable log of one growth-sampling event. Side effect on {@link PondStock}
 * (average_weight_g) is applied by {@code GrowthSampleService} in the same
 * transaction, under a PESSIMISTIC_WRITE lock on the (pond, species) row.
 */
@Entity
@Table(name = "growth_sample")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrowthSample {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "pond_id", nullable = false)
    private UUID pondId;

    @Column(nullable = false, length = 50)
    private String species;

    @Column(name = "average_weight_g", nullable = false, precision = 12, scale = 2)
    private BigDecimal averageWeightG;

    @Column(name = "sample_size", nullable = false)
    private Integer sampleSize;

    @Column(name = "sampled_at", nullable = false)
    private LocalDate sampledAt;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}