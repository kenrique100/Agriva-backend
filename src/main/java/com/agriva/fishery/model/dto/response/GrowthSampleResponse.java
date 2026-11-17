package com.agriva.fishery.model.dto.response;

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
public class GrowthSampleResponse {
    private UUID id;
    private UUID farmId;
    private UUID pondId;
    private String species;
    private BigDecimal averageWeightG;
    private Integer sampleSize;
    private LocalDate sampledAt;
    private UUID recordedByUserId;
    private LocalDateTime createdAt;
}