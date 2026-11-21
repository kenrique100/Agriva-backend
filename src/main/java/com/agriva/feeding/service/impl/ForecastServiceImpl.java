package com.agriva.feeding.service.impl;

import com.agriva.feeding.calculator.DemandForecastInput;
import com.agriva.feeding.calculator.DemandForecastResult;
import com.agriva.feeding.calculator.FeedDemandForecaster;
import com.agriva.feeding.calculator.GrowthStageResult;
import com.agriva.feeding.mapper.DemandForecastMapper;
import com.agriva.feeding.model.dto.request.DemandForecastRequest;
import com.agriva.feeding.model.dto.response.DemandForecastResponse;
import com.agriva.feeding.model.entity.DemandForecast;
import com.agriva.feeding.model.entity.DemandForecastStage;
import com.agriva.feeding.model.entity.FormulationTemplateLine;
import com.agriva.feeding.repository.DemandForecastRepository;
import com.agriva.feeding.repository.DemandForecastStageRepository;
import com.agriva.feeding.repository.FormulationTemplateLineRepository;
import com.agriva.feeding.repository.FormulationTemplateRepository;
import com.agriva.feeding.service.ForecastService;
import com.agriva.feeding.utils.IngredientCrudeProteinResolver;    // NEW (Stage 4)
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;                  // NEW (Stage 4)
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForecastServiceImpl implements ForecastService {

    private final FeedDemandForecaster forecaster;
    private final DemandForecastRepository forecastRepository;
    private final DemandForecastStageRepository stageRepository;
    private final FormulationTemplateRepository templateRepository;
    private final DemandForecastMapper mapper;
    private final IngredientCrudeProteinResolver cpResolver;
    private final FormulationTemplateLineRepository templateLineRepo;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public DemandForecastResponse createForecast(DemandForecastRequest request,
                                                 UUID farmId, UUID userId) {
        // Domain validation that Bean Validation cannot express
        if (request.getTargetWeightKg().compareTo(request.getInitialWeightKg()) <= 0) {
            throw new BusinessException(
                    "Target weight must be greater than initial weight");
        }

        // If a template was referenced, verify the caller has access to it
        if (request.getFormulaTemplateId() != null) {
            templateRepository.findByIdOrSystemForFarm(request.getFormulaTemplateId(), farmId)
                    .orElseThrow(() -> new BusinessException(
                            "Formulation template not found or not accessible for this farm"));
        }

        DemandForecastInput input = new DemandForecastInput(
                farmId,
                request.getSpecies(),
                request.getNumberOfHead(),
                request.getInitialWeightKg(),
                request.getTargetWeightKg(),
                request.getWaterTemperatureC(),
                request.getTargetCpValue(),
                request.getFormulaTemplateId()
        );

        DemandForecastResult result = forecaster.calculate(input);

        BigDecimal computedCp = computeTemplateBlendCp(
                farmId, request.getFormulaTemplateId());

        DemandForecast forecast = DemandForecast.builder()
                .farmId(farmId)
                .species(request.getSpecies())
                .numberOfHead(request.getNumberOfHead())
                .initialWeightKg(request.getInitialWeightKg())
                .targetWeightKg(request.getTargetWeightKg())
                .waterTemperatureC(request.getWaterTemperatureC())
                .targetCpValue(request.getTargetCpValue())
                .formulaTemplateId(request.getFormulaTemplateId())
                .totalFeedKg(result.totalFeedKg())
                .totalDays(result.totalDays())
                .averageDailyFeedKg(result.averageDailyFeedKg())
                .algorithmVersion(result.algorithmVersion())
                .createdByUserId(userId)
                .computedCpValue(computedCp)
                .nutrientSummaryJson(serializeNutrientSummary(
                        request.getTargetCpValue(), computedCp))
                .build();

        DemandForecast saved = forecastRepository.save(forecast);

        List<DemandForecastStage> stages = new ArrayList<>();
        int idx = 0;
        for (GrowthStageResult stage : result.stages()) {
            stages.add(DemandForecastStage.builder()
                    .forecastId(saved.getId())
                    .stageIndex(idx++)
                    .stageName(stage.stageName())
                    .startWeightKg(stage.startWeightKg())
                    .endWeightKg(stage.endWeightKg())
                    .daysInStage(stage.daysInStage())
                    .feedConsumedKg(stage.feedConsumedKg())
                    .dailyFeedRequirementKg(stage.dailyFeedRequirementKg())
                    .feedingTimesPerDay(stage.feedingTimesPerDay())
                    .build());
        }
        stageRepository.saveAll(stages);

        log.info("Forecast {} created for farm {} (species={}, algorithm={}, "
                        + "totalFeedKg={}, computedCp={})",
                saved.getId(), farmId, saved.getSpecies(),
                saved.getAlgorithmVersion(), saved.getTotalFeedKg(), computedCp);

        return mapper.toResponse(saved, stages);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DemandForecastResponse> listForecasts(UUID farmId, Pageable pageable) {
        return forecastRepository.findByFarmIdLive(farmId, pageable)
                .map(f -> {
                    List<DemandForecastStage> stages =
                            stageRepository.findByForecastIdOrderByStageIndexAsc(f.getId());
                    return mapper.toResponse(f, stages);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public DemandForecastResponse getForecast(UUID id, UUID farmId) {
        DemandForecast forecast = forecastRepository.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Forecast not found with ID: " + id));
        List<DemandForecastStage> stages =
                stageRepository.findByForecastIdOrderByStageIndexAsc(id);
        return mapper.toResponse(forecast, stages);
    }

    private BigDecimal computeTemplateBlendCp(UUID farmId, UUID templateId) {
        if (templateId == null) return null;

        List<FormulationTemplateLine> templateLines =
                templateLineRepo.findByTemplateId(templateId);
        if (templateLines.isEmpty()) return null;

        Map<String, BigDecimal> cpByLowerName = cpResolver
                .resolveByName(farmId)
                .entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().trim().toLowerCase(Locale.ROOT),
                        Map.Entry::getValue,
                        (a, b) -> a));

        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalPct    = BigDecimal.ZERO;

        for (FormulationTemplateLine line : templateLines) {
            if (line.getPercentage() == null) continue;

            BigDecimal cp = line.getIngredientName() == null
                    ? null
                    : cpByLowerName.get(line.getIngredientName().trim().toLowerCase(Locale.ROOT));
            if (cp == null) {
                cp = cpResolver.cpForName(line.getIngredientName());
            }
            if (cp == null) continue;

            weightedSum = weightedSum.add(line.getPercentage().multiply(cp));
            totalPct    = totalPct.add(line.getPercentage());
        }

        if (totalPct.signum() == 0) return null;

        return weightedSum.divide(totalPct, 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String serializeNutrientSummary(BigDecimal targetCp, BigDecimal computedCp) {
        if (computedCp == null) return null;
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "targetCp", targetCp == null ? BigDecimal.ZERO : targetCp,
                    "computedCp", computedCp));
        } catch (Exception e) {
            log.warn("Failed to serialize nutrient summary: {}", e.getMessage());
            return null;
        }
    }
}