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
import com.agriva.feeding.repository.DemandForecastRepository;
import com.agriva.feeding.repository.DemandForecastStageRepository;
import com.agriva.feeding.repository.FormulationTemplateRepository;
import com.agriva.feeding.service.ForecastService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ForecastServiceImpl implements ForecastService {

    private final FeedDemandForecaster forecaster;
    private final DemandForecastRepository forecastRepository;
    private final DemandForecastStageRepository stageRepository;
    private final FormulationTemplateRepository templateRepository;
    private final DemandForecastMapper mapper;

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

        // Delegates to the calculator. If no user calculator is wired in,
        // ManualDemandForecaster throws UnsupportedOperationException → 501.
        DemandForecastResult result = forecaster.calculate(input);

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

        log.info("Forecast {} created for farm {} (species={}, algorithm={}, totalFeedKg={})",
                saved.getId(), farmId, saved.getSpecies(),
                saved.getAlgorithmVersion(), saved.getTotalFeedKg());

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
}