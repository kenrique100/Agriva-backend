package com.agriva.poultry.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchFcrResponse {

    private UUID batchId;
    private UUID houseId;
    private LocalDate from;
    private LocalDate to;
    private BigDecimal feedConsumedKg;
    private BigDecimal initialBiomassKg;
    private BigDecimal currentBiomassKg;
    private BigDecimal weightGainKg;
    private BigDecimal fcr;
    private Long initialCount;
    private Long currentCount;
    private BigDecimal initialAvgWeightG;
    private BigDecimal currentAvgWeightG;
    private String note;
}