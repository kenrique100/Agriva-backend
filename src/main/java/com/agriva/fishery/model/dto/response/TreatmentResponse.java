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
public class TreatmentResponse {
    private UUID id;
    private UUID farmId;
    private UUID pondId;
    private String medicationName;
    private BigDecimal dosage;
    private String dosageUnit;
    private LocalDate administeredAt;
    private Integer withdrawalDays;
    private UUID administeredByUserId;
    private String notes;
    private LocalDateTime createdAt;
}