package com.agriva.feeding.service.impl;

import com.agriva.feeding.calculator.DemandForecastInput;
import com.agriva.feeding.calculator.DemandForecastResult;
import com.agriva.feeding.calculator.FeedDemandForecaster;
import com.agriva.feeding.calculator.FormulationCpCalculator;
import com.agriva.feeding.calculator.GrowthStageResult;
import com.agriva.feeding.calculator.SpeciesKey;
import com.agriva.feeding.mapper.DemandForecastMapper;
import com.agriva.feeding.model.dto.request.DemandForecastRequest;
import com.agriva.feeding.model.dto.request.ProduceFeedFromForecastRequest;
import com.agriva.feeding.model.dto.request.UpdateDemandForecastRequest;
import com.agriva.feeding.model.dto.response.DemandForecastResponse;
import com.agriva.feeding.model.dto.response.ProduceFeedFromForecastResponse;
import com.agriva.feeding.model.entity.DemandForecast;
import com.agriva.feeding.model.entity.DemandForecastStage;
import com.agriva.feeding.model.entity.FormulationTemplate;
import com.agriva.feeding.model.entity.FormulationTemplateLine;
import com.agriva.feeding.repository.DemandForecastRepository;
import com.agriva.feeding.repository.DemandForecastStageRepository;
import com.agriva.feeding.repository.FeedBatchRepository;
import com.agriva.feeding.repository.FormulationRepository;
import com.agriva.feeding.repository.FormulationTemplateLineRepository;
import com.agriva.feeding.repository.FormulationTemplateRepository;
import com.agriva.feeding.service.ForecastService;
import com.agriva.feeding.utils.IngredientCrudeProteinResolver;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private final FormulationCpCalculator cpCalculator;
    private final DemandForecastRepository forecastRepository;
    private final DemandForecastStageRepository stageRepository;
    private final FormulationTemplateRepository templateRepository;
    private final FormulationTemplateLineRepository templateLineRepo;
    private final DemandForecastMapper mapper;
    private final IngredientCrudeProteinResolver cpResolver;
    private final ObjectMapper objectMapper;
    private final FeedBatchRepository feedBatchRepository;
    private final FormulationRepository formulationRepository;
    private final ForecastProduceService forecastProduceService;

    @Override
    @Transactional
    public DemandForecastResponse createForecast(DemandForecastRequest request,
                                                 UUID farmId, UUID userId) {
        if (request.getTargetWeightKg().compareTo(request.getInitialWeightKg()) <= 0) {
            throw new BusinessException("Target weight must be greater than initial weight");
        }

        UUID resolvedTemplateId = resolveOrAttachDefaultTemplate(
                farmId, request.getSpecies(), request.getFormulaTemplateId());

        int headCount = safeHeadCount(request.getNumberOfHead());

        DemandForecastInput input = new DemandForecastInput(
                farmId,
                request.getSpecies(),
                headCount,
                request.getInitialWeightKg(),
                request.getTargetWeightKg(),
                request.getWaterTemperatureC(),
                request.getTargetCpValue(),
                resolvedTemplateId);

        DemandForecastResult result = forecaster.calculate(input);
        validateResult(result);

        BigDecimal computedCp = computeTemplateBlendCp(farmId, resolvedTemplateId);

        DemandForecast forecast = DemandForecast.builder()
                .farmId(farmId)
                .species(request.getSpecies())
                .numberOfHead(request.getNumberOfHead())
                .initialWeightKg(request.getInitialWeightKg())
                .targetWeightKg(request.getTargetWeightKg())
                .waterTemperatureC(request.getWaterTemperatureC())
                .targetCpValue(request.getTargetCpValue())
                .formulaTemplateId(resolvedTemplateId)
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

        List<DemandForecastStage> stages = buildStages(saved.getId(), result);
        stageRepository.saveAll(stages);

        log.info("Forecast {} created for farm {} (species={}, algorithm={}, "
                        + "template={}, totalFeedKg={}, computedCp={})",
                saved.getId(), farmId, saved.getSpecies(), saved.getAlgorithmVersion(),
                resolvedTemplateId, saved.getTotalFeedKg(), computedCp);

        return mapper.toResponse(saved, stages);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DemandForecastResponse> listForecasts(UUID farmId, Pageable pageable) {
        Page<DemandForecast> page = forecastRepository.findByFarmIdLive(farmId, pageable);
        if (page.isEmpty()) return page.map(f -> mapper.toResponse(f, List.of()));

        // B9: batch-load stages for the whole page.
        List<UUID> ids = page.getContent().stream().map(DemandForecast::getId).toList();
        Map<UUID, List<DemandForecastStage>> stagesByForecast =
                stageRepository.findByForecastIdInOrderByForecastIdAscStageIndexAsc(ids)
                        .stream()
                        .collect(Collectors.groupingBy(DemandForecastStage::getForecastId));

        return page.map(f -> mapper.toResponse(
                f, stagesByForecast.getOrDefault(f.getId(), List.of())));
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

    @Override
    @Transactional
    public DemandForecastResponse updateForecast(UUID id,
                                                 UpdateDemandForecastRequest request,
                                                 UUID farmId,
                                                 UUID userId) {
        DemandForecast forecast = forecastRepository
                .findByIdAndFarmIdLiveForUpdate(id, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Forecast not found with ID: " + id));

        if (request.getExpectedVersion() != null
                && !request.getExpectedVersion().equals(forecast.getVersion())) {
            throw new BusinessException(
                    "Forecast was modified by someone else (expected version "
                            + request.getExpectedVersion() + ", current "
                            + forecast.getVersion() + "). Reload and try again.");
        }

        assertForecastNotReferenced(id, farmId, "edit");

        if (request.getTargetWeightKg().compareTo(request.getInitialWeightKg()) <= 0) {
            throw new BusinessException(
                    "Target weight must be greater than initial weight");
        }

        UUID resolvedTemplateId = resolveOrAttachDefaultTemplate(
                farmId, request.getSpecies(), request.getFormulaTemplateId());

        int headCount = safeHeadCount(request.getNumberOfHead());

        DemandForecastInput input = new DemandForecastInput(
                farmId,
                request.getSpecies(),
                headCount,
                request.getInitialWeightKg(),
                request.getTargetWeightKg(),
                request.getWaterTemperatureC(),
                request.getTargetCpValue(),
                resolvedTemplateId);

        DemandForecastResult result = forecaster.calculate(input);
        validateResult(result);

        BigDecimal computedCp = computeTemplateBlendCp(farmId, resolvedTemplateId);

        stageRepository.deleteAllByForecastId(id);

        forecast.setSpecies(request.getSpecies());
        forecast.setNumberOfHead(request.getNumberOfHead());
        forecast.setInitialWeightKg(request.getInitialWeightKg());
        forecast.setTargetWeightKg(request.getTargetWeightKg());
        forecast.setWaterTemperatureC(request.getWaterTemperatureC());
        forecast.setTargetCpValue(request.getTargetCpValue());
        forecast.setFormulaTemplateId(resolvedTemplateId);
        forecast.setTotalFeedKg(result.totalFeedKg());
        forecast.setTotalDays(result.totalDays());
        forecast.setAverageDailyFeedKg(result.averageDailyFeedKg());
        forecast.setAlgorithmVersion(result.algorithmVersion());
        forecast.setComputedCpValue(computedCp);
        forecast.setNutrientSummaryJson(serializeNutrientSummary(
                request.getTargetCpValue(), computedCp));

        DemandForecast saved = forecastRepository.save(forecast);

        List<DemandForecastStage> stages = buildStages(saved.getId(), result);
        stageRepository.saveAll(stages);

        log.info("Forecast {} updated for farm {} (by={}, species={}, algorithm={}, "
                        + "template={}, totalFeedKg={}, computedCp={})",
                saved.getId(), farmId, userId, saved.getSpecies(),
                saved.getAlgorithmVersion(), resolvedTemplateId,
                saved.getTotalFeedKg(), computedCp);

        return mapper.toResponse(saved, stages);
    }

    @Override
    @Transactional
    public void deleteForecast(UUID id, UUID farmId, UUID userId) {
        DemandForecast forecast = forecastRepository
                .findByIdAndFarmIdLiveForUpdate(id, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Forecast not found with ID: " + id));

        assertForecastNotReferenced(id, farmId, "delete");

        forecast.setDeletedAt(LocalDateTime.now());
        forecastRepository.save(forecast);

        log.info("Forecast {} soft-deleted from farm {} by user {}", id, farmId, userId);
    }

    @Override
    public ProduceFeedFromForecastResponse produceFeedFromForecast(
            UUID forecastId,
            ProduceFeedFromForecastRequest request,
            UUID farmId,
            UUID userId,
            String idempotencyKey) {
        return forecastProduceService.produceFeedFromForecast(
                forecastId, request, farmId, userId, idempotencyKey);
    }

    private UUID resolveOrAttachDefaultTemplate(UUID farmId,
                                                String species,
                                                UUID requestedTemplateId) {
        if (requestedTemplateId != null) {
            templateRepository.findByIdOrSystemForFarm(requestedTemplateId, farmId)
                    .orElseThrow(() -> new BusinessException(
                            "Formulation template not found or not accessible for this farm"));
            return requestedTemplateId;
        }

        String expectedName = SpeciesKey.defaultTemplateNameFor(species);
        return templateRepository.findSystemTemplatesLive().stream()
                .filter(t -> expectedName.equals(t.getName()))
                .findFirst()
                .map(FormulationTemplate::getId)
                .orElseThrow(() -> new BusinessException(
                        "No formulaTemplateId supplied and no default template '"
                                + expectedName + "' is seeded. Seed the default for this "
                                + "species, or supply formulaTemplateId explicitly. "
                                + "Supported species: " + SpeciesKey.supported()));
    }

    private static int safeHeadCount(Long numberOfHead) {
        if (numberOfHead == null) {
            throw new BusinessException("Number of head is required");
        }
        if (numberOfHead > Integer.MAX_VALUE) {
            throw new BusinessException(
                    "Number of head must not exceed " + Integer.MAX_VALUE);
        }
        return numberOfHead.intValue();
    }

    private static void validateResult(DemandForecastResult result) {
        if (result == null
                || result.stages() == null || result.stages().isEmpty()
                || result.totalDays() <= 0
                || result.totalFeedKg() == null || result.totalFeedKg().signum() < 0) {
            throw new BusinessException("Forecaster returned an empty or invalid result");
        }
    }

    private void assertForecastNotReferenced(UUID forecastId, UUID farmId, String verb) {
        long batchCount = feedBatchRepository.countByForecastIdAndFarmId(forecastId, farmId);
        long formulationCount =
                formulationRepository.countByForecastIdAndFarmIdLive(forecastId, farmId);

        if (batchCount > 0 || formulationCount > 0) {
            throw new BusinessException(
                    "Cannot " + verb + " forecast: " + formulationCount
                            + " formulation(s) and " + batchCount
                            + " feed batch(es) reference it. Delete those first so the "
                            + "original forecast parameters remain traceable.");
        }
    }

    private List<DemandForecastStage> buildStages(UUID forecastId, DemandForecastResult result) {
        List<DemandForecastStage> stages = new ArrayList<>(result.stages().size());
        int idx = 0;
        for (GrowthStageResult stage : result.stages()) {
            stages.add(DemandForecastStage.builder()
                    .forecastId(forecastId)
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
        return stages;
    }

    private BigDecimal computeTemplateBlendCp(UUID farmId, UUID templateId) {
        if (templateId == null) return null;

        FormulationTemplate template = templateRepository
                .findByIdOrSystemForFarm(templateId, farmId)
                .orElse(null);
        if (template == null) {
            log.warn("computeTemplateBlendCp: template {} not found or soft-deleted — "
                    + "skipping CP blend", templateId);
            return null;
        }

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

        List<FormulationCpCalculator.Line> cpLines = new ArrayList<>(templateLines.size());
        for (FormulationTemplateLine line : templateLines) {
            if (line.getPercentage() == null) continue;

            BigDecimal cp = line.getCrudeProteinPct();
            if (cp == null && line.getIngredientName() != null) {
                cp = cpByLowerName.get(line.getIngredientName().trim().toLowerCase(Locale.ROOT));
            }
            if (cp == null) {
                cp = cpResolver.cpForName(line.getIngredientName());
            }
            if (cp == null) continue;

            cpLines.add(new FormulationCpCalculator.Line(line.getPercentage(), cp));
        }
        if (cpLines.isEmpty()) return null;
        return cpCalculator.weightedAverageCp(cpLines);
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

    @Override
    public ProduceFeedFromForecastResponse replayByKey(UUID farmId, String idempotencyKey) {
        return forecastProduceService.replayByKey(farmId, idempotencyKey);
    }
}