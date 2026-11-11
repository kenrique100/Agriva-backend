package com.agriva.feeding.model.dto.response;

import com.agriva.feeding.port.ConsumerType;
import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FeedingEventResponse {

    private UUID id;
    private UUID farmId;
    private ConsumerType consumerType;
    private UUID consumerId;
    private String consumerDisplayName;
    private Long consumerHeadcount;
    private String consumerStage;
    private UUID feedBatchId;
    private BigDecimal quantityKg;
    private LocalDate fedAt;
    private String notes;
    private UUID createdByUserId;
    private LocalDateTime createdAt;
}