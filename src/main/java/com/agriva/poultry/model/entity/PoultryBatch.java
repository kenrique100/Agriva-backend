package com.agriva.poultry.model.entity;

import com.agriva.poultry.model.enums.PoultryBatchStatus;
import com.agriva.poultry.model.enums.PoultrySpecies;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One flock placed in one house. {@code currentCount} is the operational
 * headcount — it starts at {@code initialCount} and is decremented by DR-8b's
 * mortality service (analogous to {@code PondStock.quantity}). DR-8a only
 * creates and closes batches; it never modifies {@code currentCount} after
 * creation.
 */
@Entity
@Table(name = "poultry_batch")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoultryBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "house_id", nullable = false)
    private UUID houseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PoultrySpecies species;

    @Column(name = "batch_code", length = 50)
    private String batchCode;

    @Column(name = "initial_count", nullable = false)
    private Long initialCount;

    @Column(name = "current_count", nullable = false)
    private Long currentCount;

    @Column(name = "arrival_date", nullable = false)
    private LocalDate arrivalDate;

    @Column(length = 30)
    private String stage;

    @Column(name = "average_weight_g", precision = 12, scale = 2)
    private BigDecimal averageWeightG;

    @Column(name = "initial_avg_weight_g", precision = 12, scale = 2)
    private BigDecimal initialAvgWeightG;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PoultryBatchStatus status = PoultryBatchStatus.ACTIVE;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}