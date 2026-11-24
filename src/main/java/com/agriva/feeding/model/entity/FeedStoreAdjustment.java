package com.agriva.feeding.model.entity;

import com.agriva.feeding.model.enums.StockAdjustmentType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "feed_store_adjustment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedStoreAdjustment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "previous_quantity_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal previousQuantityKg;

    @Column(name = "new_quantity_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal newQuantityKg;

    @Column(name = "delta_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal deltaKg;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 20)
    private StockAdjustmentType adjustmentType;

    @Column(name = "performed_by_user_id")
    private UUID performedByUserId;

    @Column(name = "performed_at", nullable = false, updatable = false)
    private LocalDateTime performedAt;

    @PrePersist
    void onCreate() {
        if (performedAt == null) performedAt = LocalDateTime.now();
    }
}