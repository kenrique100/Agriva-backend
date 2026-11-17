package com.agriva.feeding.model.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record StagedFeedingFormulationResponse(
        UUID id,
        UUID farmId,
        String status,
        String validationErrors,
        String payloadJson,
        UUID submittedByUserId,
        String submittedByEmail,
        LocalDateTime submittedAt,
        LocalDateTime lastAttemptAt,
        LocalDateTime nextRetryAt,
        LocalDateTime processedAt,
        UUID movedFormulationId,
        Integer retryCount
) {}