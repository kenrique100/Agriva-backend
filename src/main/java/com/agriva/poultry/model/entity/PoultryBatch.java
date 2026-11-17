package com.agriva.poultry.model.entity;

import com.agriva.poultry.model.enums.PoultryBatchStatus;
import com.agriva.poultry.model.enums.PoultrySpecies;
import com.agriva.shared.models.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "poultry_batch",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_pbatch_farm_code",
                columnNames = {"farm_id", "batch_code"}))
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoultryBatch extends AuditableEntity {

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

    @Column(name = "batch_code", nullable = false, length = 30, updatable = false)
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
}