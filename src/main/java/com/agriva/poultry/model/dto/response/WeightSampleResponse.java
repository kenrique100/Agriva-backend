package com.agriva.poultry.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeightSampleResponse {
    private UUID id;
    private UUID farmId;
    private UUID batchId;
    private BigDecimal averageWeightG;
    private Integer sampleSize;
    private LocalDate sampledAt;
    private UUID recordedByUserId;
    private LocalDateTime createdAt;
}