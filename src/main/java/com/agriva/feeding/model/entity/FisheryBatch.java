package com.agriva.feeding.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fishery_batch")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FisheryBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "pond_id", nullable = false)
    private UUID pondId;

    @Column(nullable = false, length = 50)
    private String species;

    @Column(name = "initial_head_count", nullable = false)
    private Long initialHeadCount;

    @Column(name = "current_head_count", nullable = false)
    private Long currentHeadCount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "expected_harvest_date")
    private LocalDate expectedHarvestDate;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}