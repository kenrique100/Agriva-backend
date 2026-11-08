package com.agriva.payroll.model.entity;

import com.agriva.payroll.model.enums.StagedSalaryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "staged_salary")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StagedSalary {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private StagedSalaryStatus status;

    @Column(name = "validation_errors", length = 2000)
    private String validationErrors;

    @Column(name = "submitted_by_user_id")
    private UUID submittedByUserId;

    @Column(name = "submitted_by_email", length = 100)
    private String submittedByEmail;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "moved_salary_id")
    private UUID movedSalaryId;

    @Column(name = "retry_count", nullable = false) @Builder.Default
    private Integer retryCount = 0;
}