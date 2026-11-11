package com.agriva.feeding.mapper;

import com.agriva.feeding.model.dto.response.DemandForecastResponse;
import com.agriva.feeding.model.entity.DemandForecast;
import com.agriva.feeding.model.entity.DemandForecastStage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DemandForecastMapper {

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
                .build();
    }
}