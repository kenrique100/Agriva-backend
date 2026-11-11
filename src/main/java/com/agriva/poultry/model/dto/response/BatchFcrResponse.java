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

    /** Feed Conversion Ratio = feedConsumed / weightGain. Null when not computable. */
    private BigDecimal fcr;

    private Long initialCount;
    private Long currentCount;
    private BigDecimal initialAvgWeightG;
    private BigDecimal currentAvgWeightG;

    /**
     * Human-readable explanation when {@link #fcr} is null — e.g.
     * "multiple active batches in house", "no weight samples",
     * "batch is not ACTIVE". Null on the happy path.
     */
    private String note;
}