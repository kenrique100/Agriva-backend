package com.agriva.model.entity;

import com.agriva.model.entity.enums.CropCycleStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "crop_cycles") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CropCycle {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "farm_id", nullable = false) private UUID farmId;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "field_id", nullable = false)
    private Field field;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "crop_id", nullable = false)
    private Crop crop;

    @Column(name = "planting_date", nullable = false)
    private LocalDate plantingDate;

    @Column(name = "expected_harvest_date")
    private LocalDate expectedHarvestDate;

    @Column(name = "actual_harvest_date")
    private LocalDate actualHarvestDate;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    @Builder.Default private CropCycleStatus status = CropCycleStatus.PLANNED;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}