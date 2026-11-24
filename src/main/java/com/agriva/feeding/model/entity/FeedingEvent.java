package com.agriva.feeding.model.entity;

import com.agriva.feeding.port.ConsumerType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "feeding_event")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "fishery_batch_id")
    private UUID fisheryBatchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "consumer_type", nullable = false, length = 20)
    private ConsumerType consumerType;

    @Column(name = "consumer_id", nullable = false)
    private UUID consumerId;

    @Column(name = "consumer_display_name", nullable = false, length = 200)
    private String consumerDisplayName;

    @Column(name = "consumer_headcount", nullable = false)
    private Long consumerHeadcount;

    @Column(name = "consumer_stage", length = 30)
    private String consumerStage;

    @Column(name = "feed_batch_id")
    private UUID feedBatchId;

    @Column(name = "quantity_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityKg;

    @Column(name = "fed_at", nullable = false)
    private LocalDate fedAt;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}