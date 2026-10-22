package com.agriva.model.dto.income;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncomeStagingResponse {
    private UUID id;
    private UUID farmId;
    private String status;
    private String validationErrors;
    private String payloadJson;
    private UUID submittedByUserId;
    private String submittedByEmail;
    private LocalDateTime submittedAt;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime nextRetryAt;
    private LocalDateTime processedAt;
    private UUID movedIncomeId;
    private Integer retryCount;
}