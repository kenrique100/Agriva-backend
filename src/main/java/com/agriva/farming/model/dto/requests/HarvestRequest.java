package com.agriva.farming.model.dto.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class HarvestRequest {
    @NotNull
    private LocalDate harvestDate;

    @NotNull
    @Positive
    private BigDecimal quantity;

    private String unit = "KG";
    private String qualityGrade;
    private UUID linkedIncomeId;
}