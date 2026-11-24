package com.agriva.feeding.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "farm_feed_store",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_farm_feed_store_farm",
                columnNames = "farm_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmFeedStore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "quantity_on_hand_kg", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal quantityOnHandKg = BigDecimal.ZERO;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.quantityOnHandKg == null) this.quantityOnHandKg = BigDecimal.ZERO;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}