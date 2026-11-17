package com.agriva.feeding.model.dto.response;

import com.agriva.feeding.calculator.FormulationMode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FormulationResponse {

    private UUID id;
    private UUID farmId;
    private String name;
    private String targetSpecies;
    private FormulationMode mode;
    private BigDecimal targetQuantityKg;
    private BigDecimal targetCpValue;
    private UUID formulaTemplateId;
    private BigDecimal totalKg;
    private BigDecimal totalCost;
    private String algorithmVersion;
    private Map<String, BigDecimal> nutrientSummary;
    private LocalDateTime createdAt;
    private List<Line> lines;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private UUID ingredientId;
        private String ingredientName;
        private BigDecimal quantityKg;
        private BigDecimal percentage;
        private BigDecimal unitCost;
        private BigDecimal lineCost;
    }
}