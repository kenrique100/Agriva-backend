package com.agriva.feeding.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class FeedBatchResponse {
    private UUID id;
    private UUID farmId;
    private UUID formulationId;
    private UUID forecastId;
    private String formulationName;
    private BigDecimal producedKg;
    private BigDecimal bagSizeKg;
    private Integer numberOfBags;
    private BigDecimal bulkRemainderKg;
    private BigDecimal costTotal;
    private LocalDate productionDate;
    private String notes;
    private UUID createdByUserId;
    private LocalDateTime createdAt;
}