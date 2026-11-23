package com.agriva.feeding.model.dto.response;

import com.agriva.feeding.model.enums.StockAdjustmentType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeedStoreAdjustmentResponse {
    private UUID id;
    private UUID farmId;
    private BigDecimal previousQuantityKg;
    private BigDecimal newQuantityKg;
    private BigDecimal deltaKg;
    private String reason;
    private StockAdjustmentType adjustmentType;
    private UUID performedByUserId;
    private LocalDateTime performedAt;
}