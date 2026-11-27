package com.agriva.feeding.service.impl;

import com.agriva.feeding.calculator.*;
import com.agriva.feeding.mapper.FormulationMapper;
import com.agriva.feeding.model.dto.request.FormulationRequest;
import com.agriva.feeding.model.dto.response.FormulationResponse;
import com.agriva.feeding.model.entity.Formulation;
import com.agriva.feeding.model.entity.FormulationLine;
import com.agriva.feeding.model.entity.Ingredient;
import com.agriva.feeding.model.entity.IngredientStock;
import com.agriva.feeding.repository.DemandForecastRepository;
import com.agriva.feeding.repository.FormulationLineRepository;
import com.agriva.feeding.repository.FormulationRepository;
import com.agriva.feeding.repository.FormulationTemplateRepository;
import com.agriva.feeding.repository.IngredientRepository;
import com.agriva.feeding.repository.IngredientStockRepository;
import com.agriva.feeding.service.FormulationComputationService;
import com.agriva.feeding.utils.IngredientCrudeProteinResolver;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FormulationComputationServiceImpl implements FormulationComputationService {

    private final FeedFormulationCalculator calculator;
    private final FormulationRepository formulationRepository;
    private final FormulationLineRepository lineRepository;
    private final IngredientRepository ingredientRepository;
    private final IngredientStockRepository stockRepository;
    private final FormulationTemplateRepository templateRepository;
    private final DemandForecastRepository demandForecastRepository;
    private final FormulationMapper mapper;
    private final ObjectMapper objectMapper;
    private final IngredientCrudeProteinResolver cpResolver;

    @Override
    @Transactional
    public Computed computeAndPersist(FormulationRequest request, UUID farmId, UUID userId) {
        // Fast-path pre-check
        if (formulationRepository.findMostRecentByFarmIdAndNameLive(farmId, request.getName()).isPresent()) {
            throw new BusinessException(
                    "A formulation with the name '" + request.getName() + "' already exists on this farm");
        }

        // Mode-specific guards
        if (request.getMode() == FormulationMode.ALGORITHM
                && (request.getTargetQuantityKg() == null
                || request.getTargetQuantityKg().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BusinessException("targetQuantityKg is required for ALGORITHM mode");
        }
        if (request.getMode() == FormulationMode.CUSTOM
                && (request.getLines() == null || request.getLines().isEmpty())) {
            throw new BusinessException("lines[] is required for CUSTOM mode");
        }
        if (request.getMode() == FormulationMode.ALGORITHM
                && request.getLines() != null && !request.getLines().isEmpty()) {
            throw new BusinessException(
                    "ALGORITHM mode derives ingredients from the template and does not "
                            + "accept explicit ingredient lines.");
        }
        if (request.getMode() == FormulationMode.CUSTOM) {
            for (var line : request.getLines()) {
                if (line.getQuantityKg() == null || line.getQuantityKg().signum() <= 0) {
                    throw new BusinessException(
                            "Line quantityKg must be > 0 for ingredient " + line.getIngredientId());
                }
            }
        }
        if (!calculator.supportedModes().contains(request.getMode())) {
            throw new UnsupportedOperationException(
                    "Calculator does not support mode " + request.getMode());
        }
        if (request.getFormulaTemplateId() != null) {
            templateRepository.findByIdOrSystemForFarm(request.getFormulaTemplateId(), farmId)
                    .orElseThrow(() -> new BusinessException(
                            "Formulation template not found or not accessible"));
        }
        if (request.getForecastId() != null) {
            demandForecastRepository.findByIdAndFarmIdLive(request.getForecastId(), farmId)
                    .orElseThrow(() -> new BusinessException(
                            "Forecast not found or not accessible on this farm: "
                                    + request.getForecastId()));
        }

        // Build the calculator input
        List<Ingredient> ingredients = ingredientRepository.findAllByFarmIdLive(farmId);
        Map<UUID, IngredientStock> stockById = stockRepository.findAllByFarmId(farmId).stream()
                .collect(Collectors.toMap(IngredientStock::getIngredientId, s -> s));
        Map<UUID, String> nameById = ingredients.stream()
                .collect(Collectors.toMap(Ingredient::getId, Ingredient::getName));
        Map<UUID, BigDecimal> cpById = cpResolver.resolveByIngredientId(farmId);

        List<AvailableIngredient> available = ingredients.stream()
                .map(i -> {
                    IngredientStock s = stockById.get(i.getId());
                    BigDecimal qty = s == null ? BigDecimal.ZERO : s.getQuantityOnHand();
                    return new AvailableIngredient(
                            i.getId(), i.getName(), qty, i.getUnit().name(),
                            i.getCostPerUnit(), cpById.get(i.getId()), i.getDensityKgPerL());
                })
                .toList();

        List<ManualLine> manualLines = request.getMode() == FormulationMode.CUSTOM
                ? request.getLines().stream()
                  .map(l -> new ManualLine(l.getIngredientId(), l.getQuantityKg()))
                  .toList()
                : List.of();

        FormulationInput input = new FormulationInput(
                farmId, request.getMode(), request.getTargetSpecies(),
                request.getTargetQuantityKg(), request.getTargetCpValue(),
                available, manualLines, request.getFormulaTemplateId());

        FormulationResult result = calculator.calculate(input);

        for (FormulationLineResult line : result.lines()) {
            AvailableIngredient ai = available.stream()
                    .filter(a -> a.ingredientId().equals(line.ingredientId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Calculator returned an unknown ingredientId: " + line.ingredientId()));
            if (ai.quantityOnHand().compareTo(line.quantityInStockUnit()) < 0) {
                throw new com.agriva.shared.exception.InsufficientIngredientQuantityException(
                        ai.name(), line.quantityInStockUnit(), ai.quantityOnHand(), ai.unit());
            }
        }

        BigDecimal effectiveTargetKg = request.getTargetQuantityKg() != null
                ? request.getTargetQuantityKg()
                : result.totalKg();

        Formulation formulation = Formulation.builder()
                .farmId(farmId)
                .name(request.getName().trim())
                .targetSpecies(request.getTargetSpecies())
                .mode(request.getMode())
                .targetQuantityKg(effectiveTargetKg)
                .targetCpValue(request.getTargetCpValue())
                .formulaTemplateId(request.getFormulaTemplateId())
                .forecastId(request.getForecastId())
                .totalKg(result.totalKg())
                .totalCost(result.totalCost())
                .algorithmVersion(result.algorithmVersion())
                .nutrientSummaryJson(serialize(result.nutrientSummary()))
                .createdByUserId(userId)
                .build();

        Formulation saved;
        try {
            saved = formulationRepository.saveAndFlush(formulation);
        } catch (DataIntegrityViolationException e) {
            if (isFormulationNameViolation(e)) {
                throw new BusinessException(
                        "A formulation with the name '" + request.getName()
                                + "' already exists on this farm");
            }
            throw e;
        }

        List<FormulationLine> lines = new ArrayList<>(result.lines().size());
        for (FormulationLineResult lr : result.lines()) {
            String name = nameById.get(lr.ingredientId());
            if (name == null) {
                throw new ResourceNotFoundException(
                        "Ingredient name lookup failed for " + lr.ingredientId());
            }
            BigDecimal lineCost = lr.unitCost() == null
                    ? BigDecimal.ZERO
                    : lr.unitCost().multiply(lr.quantityInStockUnit());
            lines.add(FormulationLine.builder()
                    .formulationId(saved.getId())
                    .ingredientId(lr.ingredientId())
                    .ingredientName(name)
                    .quantityInStockUnit(lr.quantityInStockUnit())
                    .percentage(lr.percentage())
                    .unitCost(lr.unitCost())
                    .lineCost(lineCost)
                    .crudeProteinPct(cpById.get(lr.ingredientId()))
                    .build());
        }
        lineRepository.saveAll(lines);

        log.info("Formulation {} computed (mode={}, algorithm={}, totalKg={}, lines={}, "
                        + "forecast={})",
                saved.getId(), saved.getMode(), saved.getAlgorithmVersion(),
                saved.getTotalKg(), lines.size(), saved.getForecastId());

        return new Computed(saved, lines);
    }

    @Override
    public FormulationResponse toResponse(Computed computed) {
        return mapper.toResponse(computed.formulation(), computed.lines());
    }

    private String serialize(Map<String, BigDecimal> summary) {
        if (summary == null || summary.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            log.warn("Failed to serialize nutrientSummary: {}", e.getMessage());
            return null;
        }
    }

    private static boolean isFormulationNameViolation(DataIntegrityViolationException e) {
        String msg = e.getMostSpecificCause().getMessage();
        return msg != null && msg.contains("ux_formulation_farm_name_live");
    }
}