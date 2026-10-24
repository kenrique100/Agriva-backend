package com.agriva.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "fields") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Field {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "size_hectares", precision = 10, scale = 2)
    private BigDecimal sizeHectares;

    @Column(name = "soil_type", length = 50)
    private String soilType;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}