package com.agriva.poultry.model.dto.response;

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
public class PoultryHouseResponse {

    private UUID id;
    private UUID farmId;
    private String name;
    private String location;
    private Integer capacity;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Present only on the detail endpoint ({@code GET /houses/{id}}). */
    private BatchSummary batches;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BatchSummary {
        private long totalCurrentCount;
        private long totalInitialCount;
        private int batchCount;
        private List<String> species;
        private BigDecimal averageWeightG;
        private String dominantStage;
    }
}