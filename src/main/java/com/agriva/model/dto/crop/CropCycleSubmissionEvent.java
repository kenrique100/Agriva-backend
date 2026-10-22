package com.agriva.model.dto.crop;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Data @NoArgsConstructor @AllArgsConstructor
public class CropCycleSubmissionEvent {
    private UUID submissionId;
    private UUID farmId;
    private UUID submittedByUserId;
    private String submittedByEmail;
    private String payloadJson;
    private LocalDateTime submittedAt;
}