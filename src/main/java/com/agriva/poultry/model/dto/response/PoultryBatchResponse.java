package com.agriva.poultry.model.dto.response;

import com.agriva.poultry.model.enums.PoultryBatchStatus;
import com.agriva.poultry.model.enums.PoultrySpecies;
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
public class PoultryBatchResponse {
    private UUID id;
    private UUID farmId;
    private UUID houseId;
    private String houseName;
    private PoultrySpecies species;
    private String batchCode;
    private Long initialCount;
    private Long currentCount;
    private LocalDate arrivalDate;
    private String stage;
    private BigDecimal averageWeightG;
    private BigDecimal initialAvgWeightG;
    private PoultryBatchStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}