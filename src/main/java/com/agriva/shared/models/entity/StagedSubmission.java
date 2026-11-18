package com.agriva.shared.models.entity;

import com.agriva.shared.models.enums.StagedStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public interface StagedSubmission {

    UUID getId();
    UUID getFarmId();
    UUID getSubmittedByUserId();
    String getPayloadJson();

    StagedStatus getStatus();
    void setStatus(StagedStatus status);

    LocalDateTime getNextRetryAt();
    void setNextRetryAt(LocalDateTime nextRetryAt);

    LocalDateTime getLastAttemptAt();
    void setLastAttemptAt(LocalDateTime lastAttemptAt);

    LocalDateTime getProcessedAt();
    void setProcessedAt(LocalDateTime processedAt);

    Integer getRetryCount();
    void setRetryCount(Integer retryCount);

    String getValidationErrors();
    void setValidationErrors(String validationErrors);
}