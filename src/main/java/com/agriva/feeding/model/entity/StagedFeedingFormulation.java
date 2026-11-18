package com.agriva.feeding.model.entity;

import com.agriva.shared.models.enums.StagedStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "staged_feeding_formulation")
@IdClass(StagedFeedingFormulationId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StagedFeedingFormulation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Id
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StagedStatus status;

    @Column(name = "validation_errors", length = 2000)
    private String validationErrors;

    @Column(name = "submitted_by_user_id")
    private UUID submittedByUserId;

    @Column(name = "submitted_by_email", length = 100)
    private String submittedByEmail;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "moved_formulation_id")
    private UUID movedFormulationId;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Version
    @Column(nullable = false)
    private Long version;
}