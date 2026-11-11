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
public class EggRecordResponse {
    private UUID id;
    private UUID farmId;
    private UUID batchId;
    private Integer eggsCollected;
    private Integer brokenEggs;
    private LocalDate recordedAt;
    private UUID recordedByUserId;
    private LocalDateTime createdAt;
}