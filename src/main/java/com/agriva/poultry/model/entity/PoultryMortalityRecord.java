package com.agriva.poultry.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "poultry_mortality_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoultryMortalityRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

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