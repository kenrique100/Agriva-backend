package com.agriva.feeding.mapper;

import com.agriva.feeding.model.dto.response.DemandForecastResponse;
import com.agriva.feeding.model.entity.DemandForecast;
import com.agriva.feeding.model.entity.DemandForecastStage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DemandForecastMapper {

    private final ObjectMapper objectMapper;

    public DemandForecastResponse toResponse(DemandForecast f, List<DemandForecastStage> stages) {
        List<DemandForecastResponse.Stage> stageDtos = stages.stream()
                .map(s -> DemandForecastResponse.Stage.builder()
                        .stageIndex(s.getStageIndex())
                        .stageName(s.getStageName())
                        .startWeightKg(s.getStartWeightKg())
                        .endWeightKg(s.getEndWeightKg())
                        .daysInStage(s.getDaysInStage())
                        .feedConsumedKg(s.getFeedConsumedKg())
                        .dailyFeedRequirementKg(s.getDailyFeedRequirementKg())
                        .feedingTimesPerDay(s.getFeedingTimesPerDay())
                        .build())
                .toList();

        return DemandForecastResponse.builder()
                .id(f.getId())
                .farmId(f.getFarmId())
                .species(f.getSpecies())
                .numberOfHead(f.getNumberOfHead())
                .initialWeightKg(f.getInitialWeightKg())
                .targetWeightKg(f.getTargetWeightKg())
                .waterTemperatureC(f.getWaterTemperatureC())
                .targetCpValue(f.getTargetCpValue())
                .formulaTemplateId(f.getFormulaTemplateId())
                .totalFeedKg(f.getTotalFeedKg())
                .totalDays(f.getTotalDays())
                .averageDailyFeedKg(f.getAverageDailyFeedKg())
                .algorithmVersion(f.getAlgorithmVersion())
                .createdAt(f.getCreatedAt())
                .stages(stageDtos)
                .computedCpValue(f.getComputedCpValue())
                .nutrientSummary(parseSummary(f.getNutrientSummaryJson()))
                .build();
    }

    private Map<String, BigDecimal> parseSummary(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse nutrientSummaryJson: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}