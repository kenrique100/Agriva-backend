package com.agriva.feeding.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per farm — the farm's running feed balance in kg.
 * Lazily created on the first production. Every write acquires a
 * {@code PESSIMISTIC_WRITE} lock on this row so productions and (in DR-5)
 * feedings serialize correctly.
 */
@Entity
@Table(name = "farm_feed_store")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmFeedStore {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false, unique = true)
    private UUID farmId;

    @Column(name = "quantity_on_hand_kg", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal quantityOnHandKg = BigDecimal.ZERO;

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