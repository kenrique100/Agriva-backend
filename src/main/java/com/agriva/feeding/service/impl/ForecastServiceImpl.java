package com.agriva.feeding.service.impl;

import com.agriva.feeding.calculator.DemandForecastInput;
import com.agriva.feeding.calculator.DemandForecastResult;
import com.agriva.feeding.calculator.FeedDemandForecaster;
import com.agriva.feeding.calculator.FormulationCpCalculator;
import com.agriva.feeding.calculator.FormulationMode;
import com.agriva.feeding.calculator.GrowthStageResult;
import com.agriva.feeding.mapper.DemandForecastMapper;
import com.agriva.feeding.mapper.FeedBatchMapper;
import com.agriva.feeding.mapper.FormulationMapper;
import com.agriva.feeding.model.dto.request.DemandForecastRequest;
import com.agriva.feeding.model.dto.request.FormulationRequest;
import com.agriva.feeding.model.dto.request.ProduceFeedFromForecastRequest;
import com.agriva.feeding.model.dto.request.ProductionRequest;
import com.agriva.feeding.model.dto.request.UpdateDemandForecastRequest;
import com.agriva.feeding.model.dto.response.DemandForecastResponse;
import com.agriva.feeding.model.dto.response.FeedBatchResponse;
import com.agriva.feeding.model.dto.response.FormulationResponse;
import com.agriva.feeding.model.dto.response.ProduceFeedFromForecastResponse;
import com.agriva.feeding.model.entity.DemandForecast;
import com.agriva.feeding.model.entity.DemandForecastStage;
import com.agriva.feeding.model.entity.FeedBatch;
import com.agriva.feeding.model.entity.Formulation;
import com.agriva.feeding.model.entity.FormulationLine;
import com.agriva.feeding.model.entity.FormulationTemplate;
import com.agriva.feeding.model.entity.FormulationTemplateLine;
import com.agriva.feeding.repository.DemandForecastRepository;
import com.agriva.feeding.repository.DemandForecastStageRepository;
import com.agriva.feeding.repository.FeedBatchRepository;
import com.agriva.feeding.repository.FormulationLineRepository;
import com.agriva.feeding.repository.FormulationRepository;
import com.agriva.feeding.repository.FormulationTemplateLineRepository;
import com.agriva.feeding.repository.FormulationTemplateRepository;
import com.agriva.feeding.service.ForecastService;
import com.agriva.feeding.service.FormulationService;
import com.agriva.feeding.service.ProductionService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private static final DateTimeFormatter AUTO_NAME_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final FeedDemandForecaster forecaster;
    private final FormulationCpCalculator cpCalculator;
    private final DemandForecastRepository forecastRepository;
    private final DemandForecastStageRepository stageRepository;
    private final FormulationTemplateRepository templateRepository;
    private final DemandForecastMapper mapper;
    private final IngredientCrudeProteinResolver cpResolver;
    private final FormulationTemplateLineRepository templateLineRepo;
    private final ObjectMapper objectMapper;
    private final FeedBatchRepository feedBatchRepository;
    private final FormulationRepository formulationRepository;
    private final FormulationLineRepository formulationLineRepository;
    private final FormulationService formulationService;
    private final ProductionService productionService;
    private final FormulationMapper formulationMapper;
    private final FeedBatchMapper feedBatchMapper;

    @Override
    @Transactional
    public DemandForecastResponse createForecast(DemandForecastRequest request,
                                                 UUID farmId, UUID userId) {
        if (request.getTargetWeightKg().compareTo(request.getInitialWeightKg()) <= 0) {
            throw new BusinessException(
                    "Target weight must be greater than initial weight");
        }

        if (request.getFormulaTemplateId() != null) {
            templateRepository.findByIdOrSystemForFarm(request.getFormulaTemplateId(), farmId)
                    .orElseThrow(() -> new BusinessException(
                            "Formulation template not found or not accessible for this farm"));
        }

        Long headLong = request.getNumberOfHead();
        if (headLong == null) {
            throw new BusinessException("Number of head is required");
        }
        int headCount = headLong.intValue();

        DemandForecastInput input = new DemandForecastInput(
                farmId,
                request.getSpecies(),
                headCount,
                request.getInitialWeightKg(),
                request.getTargetWeightKg(),
                request.getWaterTemperatureC(),
                request.getTargetCpValue(),
                request.getFormulaTemplateId());

        DemandForecastResult result = forecaster.calculate(input);

        if (result == null
                || result.stages() == null || result.stages().isEmpty()
                || result.totalDays() <= 0
                || result.totalFeedKg() == null || result.totalFeedKg().signum() < 0) {
            throw new BusinessException("Forecaster returned an empty or invalid result");
        }

        BigDecimal computedCp = computeTemplateBlendCp(farmId, request.getFormulaTemplateId());

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

        List<DemandForecastStage> stages = buildStages(saved.getId(), result);
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

        if (request.getFormulaTemplateId() != null) {
            templateRepository.findByIdOrSystemForFarm(request.getFormulaTemplateId(), farmId)
                    .orElseThrow(() -> new BusinessException(
                            "Formulation template not found or not accessible for this farm"));
        }

        Long headLong = request.getNumberOfHead();
        if (headLong == null) {
            throw new BusinessException("Number of head is required");
        }
        int headCount = headLong.intValue();

        DemandForecastInput input = new DemandForecastInput(
                farmId,
                request.getSpecies(),
                headCount,
                request.getInitialWeightKg(),
                request.getTargetWeightKg(),
                request.getWaterTemperatureC(),
                request.getTargetCpValue(),
                request.getFormulaTemplateId());

        DemandForecastResult result = forecaster.calculate(input);

        if (result == null
                || result.stages() == null || result.stages().isEmpty()
                || result.totalDays() <= 0
                || result.totalFeedKg() == null || result.totalFeedKg().signum() < 0) {
            throw new BusinessException("Forecaster returned an empty or invalid result");
        }

        BigDecimal computedCp = computeTemplateBlendCp(farmId, request.getFormulaTemplateId());

        stageRepository.deleteAllByForecastId(id);

        forecast.setSpecies(request.getSpecies());
        forecast.setNumberOfHead(request.getNumberOfHead());
        forecast.setInitialWeightKg(request.getInitialWeightKg());
        forecast.setTargetWeightKg(request.getTargetWeightKg());
        forecast.setWaterTemperatureC(request.getWaterTemperatureC());
        forecast.setTargetCpValue(request.getTargetCpValue());
        forecast.setFormulaTemplateId(request.getFormulaTemplateId());
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
                        + "totalFeedKg={}, computedCp={})",
                saved.getId(), farmId, userId, saved.getSpecies(),
                saved.getAlgorithmVersion(), saved.getTotalFeedKg(), computedCp);

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
    @Transactional
    public ProduceFeedFromForecastResponse produceFeedFromForecast(
            UUID forecastId,
            ProduceFeedFromForecastRequest request,
            UUID farmId,
            UUID userId,
            String idempotencyKey) {

        // 1. Idempotency pre-check — same key = same response, no side effects.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = feedBatchRepository.findByIdempotencyKeyAndFarmId(
                    idempotencyKey, farmId);
            if (existing.isPresent()) {
                log.info("Produce-feed replayed for key {} — returning existing batch {}",
                        idempotencyKey, existing.get().getId());
                return replay(existing.get(), farmId);
            }
        }

        // 2. Load forecast under PESSIMISTIC_WRITE so a concurrent edit/delete
        //    cannot slip between our validations and the formulation creation.
        DemandForecast forecast = forecastRepository
                .findByIdAndFarmIdLiveForUpdate(forecastId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Forecast not found with ID: " + forecastId));

        // 3. Resolve the formulation template.
        UUID templateId = resolveTemplateIdForProduce(forecast, farmId);

        // 4. Build the formulation request (ALGORITHM mode, template-driven).
        String formulationName = (request.getFormulationName() != null
                && !request.getFormulationName().isBlank())
                ? request.getFormulationName().trim()
                : autoFormulationName(forecast);

        FormulationRequest formReq = new FormulationRequest();
        formReq.setName(formulationName);
        formReq.setTargetSpecies(forecast.getSpecies());
        formReq.setMode(FormulationMode.ALGORITHM);
        formReq.setTargetQuantityKg(request.getProducedKg());
        formReq.setTargetCpValue(forecast.getTargetCpValue());
        formReq.setFormulaTemplateId(templateId);
        formReq.setForecastId(forecastId);

        // 5. Create the formulation — joins this transaction.
        FormulationResponse formulationResp =
                formulationService.createFormulation(formReq, farmId, userId);

        // 6. Build the production request.
        LocalDate productionDate = request.getProductionDate() != null
                ? request.getProductionDate() : LocalDate.now();

        ProductionRequest prodReq = new ProductionRequest();
        prodReq.setFormulationId(formulationResp.getId());
        prodReq.setProducedKg(request.getProducedKg());
        prodReq.setProductionDate(productionDate);
        prodReq.setForecastId(forecastId);
        prodReq.setIdempotencyKey(
                (idempotencyKey != null && !idempotencyKey.isBlank())
                        ? idempotencyKey : null);
        prodReq.setNotes(request.getNotes());

        FeedBatchResponse batchResp =
                productionService.produce(prodReq, farmId, userId);

        log.info("Produce-from-forecast success: forecast={} formulation={} batch={} "
                        + "produced={}kg by user {}",
                forecastId, formulationResp.getId(), batchResp.getId(),
                request.getProducedKg(), userId);

        return ProduceFeedFromForecastResponse.builder()
                .formulation(formulationResp)
                .feedBatch(batchResp)
                .replayed(false)
                .build();
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

    private ProduceFeedFromForecastResponse replay(FeedBatch batch, UUID farmId) {
        Formulation formulation = formulationRepository
                .findByIdAndFarmIdLive(batch.getFormulationId(), farmId)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotent replay: batch " + batch.getId()
                                + " references formulation " + batch.getFormulationId()
                                + " that is not found on farm " + farmId));

        List<FormulationLine> lines = formulationLineRepository
                .findByFormulationIdOrderByPercentageDesc(formulation.getId());

        return ProduceFeedFromForecastResponse.builder()
                .formulation(formulationMapper.toResponse(formulation, lines))
                .feedBatch(feedBatchMapper.toResponse(batch))
                .replayed(true)
                .build();
    }

    private UUID resolveTemplateIdForProduce(DemandForecast forecast, UUID farmId) {
        if (forecast.getFormulaTemplateId() != null) {
            templateRepository
                    .findByIdOrSystemForFarm(forecast.getFormulaTemplateId(), farmId)
                    .orElseThrow(() -> new BusinessException(
                            "Forecast references template " + forecast.getFormulaTemplateId()
                                    + " which no longer exists or is not accessible on this farm"));
            return forecast.getFormulaTemplateId();
        }

        String speciesUpper = forecast.getSpecies() == null
                ? "" : forecast.getSpecies().trim().toUpperCase(Locale.ROOT);
        String expected = "AGRIVA_DEFAULT_" + speciesUpper + "_GROWER";

        return templateRepository.findSystemTemplatesLive().stream()
                .filter(t -> expected.equals(t.getName()))
                .findFirst()
                .map(FormulationTemplate::getId)
                .orElseThrow(() -> new BusinessException(
                        "Forecast has no formulaTemplateId and no default template '"
                                + expected + "' is seeded. Set formulaTemplateId on "
                                + "the forecast first (PUT /forecasts/{id})."));
    }

    private String autoFormulationName(DemandForecast forecast) {
        String shortId = forecast.getId().toString().substring(0, 8);
        String ts = LocalDateTime.now().format(AUTO_NAME_TS);
        return forecast.getSpecies() + " Feed " + shortId + "-" + ts;
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
}