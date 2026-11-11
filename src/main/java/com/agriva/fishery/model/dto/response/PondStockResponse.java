package com.agriva.fishery.model.dto.response;

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
public class PondStockResponse {
    private UUID id;
    private UUID pondId;
    private String species;
    private Long quantity;
    private BigDecimal averageWeightG;
    private String stage;
    private LocalDate stockedAt;
}