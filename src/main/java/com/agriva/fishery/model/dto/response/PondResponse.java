package com.agriva.fishery.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PondResponse {

    private UUID id;
    private UUID farmId;
    private String name;
    private String location;
    private BigDecimal surfaceAreaM2;
    private BigDecimal depthM;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private StockSummary stock;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class StockSummary {
        private long totalQuantity;
        private List<String> species;
        private BigDecimal averageWeightG;
        private String dominantStage;
    }
}