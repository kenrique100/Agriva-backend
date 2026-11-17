package com.agriva.finance.model.entity;

import com.agriva.finance.model.enums.StagedIncomeStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "staged_income")
@IdClass(StagedIncomeId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StagedIncome {

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
    private StagedIncomeStatus status;

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

    @Column(name = "moved_income_id")
    private UUID movedIncomeId;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Version
    @Column(nullable = false)
    private Long version;
}