package com.agriva.fishery.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable log of one mortality event. Side effect on {@link PondStock}
 * is applied by {@code MortalityService} in the same transaction, under a
 * PESSIMISTIC_WRITE lock on the (pond, species) row.
 */
@Entity
@Table(name = "mortality_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MortalityRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "pond_id", nullable = false)
    private UUID pondId;

    @Column(nullable = false, length = 50)
    private String species;

    @Column(nullable = false)
    private Long quantity;

    @Column(name = "recorded_at", nullable = false)
    private LocalDate recordedAt;

    @Column(name = "suspected_cause", length = 500)
    private String suspectedCause;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}