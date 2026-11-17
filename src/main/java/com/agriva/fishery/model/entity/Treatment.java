package com.agriva.fishery.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable log of one medication administration to a pond.
 * No soft-delete, no {@code @Version}: created once, never edited.
 */
@Entity
@Table(name = "treatment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Treatment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "pond_id", nullable = false)
    private UUID pondId;

    @Column(name = "medication_name", nullable = false, length = 100)
    private String medicationName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal dosage;

    @Column(name = "dosage_unit", nullable = false, length = 20)
    private String dosageUnit;

    @Column(name = "administered_at", nullable = false)
    private LocalDate administeredAt;

    @Column(name = "withdrawal_days")
    private Integer withdrawalDays;

    @Column(name = "administered_by_user_id")
    private UUID administeredByUserId;

    @Column(length = 500)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}