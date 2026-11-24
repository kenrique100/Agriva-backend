package com.agriva.feeding.model.dto.response;

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
public class DemandForecastResponse {

    private UUID id;
    private UUID farmId;
    private String species;
    private Long numberOfHead;
    private BigDecimal initialWeightKg;
    private BigDecimal targetWeightKg;
    private BigDecimal waterTemperatureC;
    private BigDecimal targetCpValue;
    private UUID formulaTemplateId;
    private BigDecimal totalFeedKg;
    private Integer totalDays;
    private BigDecimal averageDailyFeedKg;
    private String algorithmVersion;
    private LocalDateTime createdAt;
    private List<Stage> stages;
    private BigDecimal computedCpValue;
    private Map<String, BigDecimal> nutrientSummary;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stage {
        private Integer stageIndex;
        private String stageName;
        private BigDecimal startWeightKg;
        private BigDecimal endWeightKg;
        private Integer daysInStage;
        private BigDecimal feedConsumedKg;
        private BigDecimal dailyFeedRequirementKg;
        private Integer feedingTimesPerDay;
    }
}