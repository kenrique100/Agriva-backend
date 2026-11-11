package com.agriva.poultry.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MortalityRecordResponse {
    private UUID id;
    private UUID farmId;
    private UUID batchId;
    private Long quantity;
    private LocalDate recordedAt;
    private String suspectedCause;
    private UUID recordedByUserId;
    private LocalDateTime createdAt;
}