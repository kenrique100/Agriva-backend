package com.agriva.model.dto.task;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskSubmissionEvent {
    private UUID submissionId;
    private UUID farmId;
    private UUID submittedByUserId;
    private String submittedByEmail;
    private String payloadJson;
    private LocalDateTime submittedAt;
}