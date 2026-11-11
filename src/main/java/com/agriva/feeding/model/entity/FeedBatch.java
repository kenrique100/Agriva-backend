package com.agriva.feeding.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A physical production run. Immutable once persisted — corrections go through
 * a future FeedBatchAdjustment slice. {@code formulationName} is snapshotted so
 * batch history survives formulation soft-deletion. {@code bagSizeKg} is
 * snapshot from the running config so historic reports stay accurate even if
 * the global default later changes.
 */
@Entity
@Table(name = "feed_batch")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "formulation_id", nullable = false)
    private UUID formulationId;

    @Column(name = "formulation_name", nullable = false, length = 100)
    private String formulationName;

    @Column(name = "produced_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal producedKg;

    @Column(name = "bag_size_kg", nullable = false, precision = 10, scale = 4)
    private BigDecimal bagSizeKg;

    @Column(name = "number_of_bags", nullable = false)
    private Integer numberOfBags;

    @Column(name = "bulk_remainder_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal bulkRemainderKg;

    @Column(name = "cost_total", precision = 19, scale = 4)
    private BigDecimal costTotal;

    @Column(name = "production_date", nullable = false)
    private LocalDate productionDate;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}