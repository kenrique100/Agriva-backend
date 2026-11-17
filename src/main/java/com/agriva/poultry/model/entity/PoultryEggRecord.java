package com.agriva.poultry.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "poultry_egg_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PoultryEggRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "eggs_collected", nullable = false)
    private Integer eggsCollected;

    @Column(name = "broken_eggs", nullable = false)
    @Builder.Default
    private Integer brokenEggs = 0;

    @Column(name = "recorded_at", nullable = false)
    private LocalDate recordedAt;

    @Column(name = "recorded_by_user_id")
    private UUID recordedByUserId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}