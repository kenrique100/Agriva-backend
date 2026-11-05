package com.agriva.farming.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HarvestResponse {
    private UUID id;
    private UUID cropCycleId;
    private LocalDate harvestDate;
    private BigDecimal quantity;
    private String unit;
    private String qualityGrade;
    private UUID linkedIncomeId;
}