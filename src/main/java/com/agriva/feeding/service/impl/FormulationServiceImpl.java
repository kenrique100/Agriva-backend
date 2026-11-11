package com.agriva.feeding.service.impl;

import com.agriva.feeding.calculator.*;
import com.agriva.feeding.mapper.FormulationMapper;
import com.agriva.feeding.model.dto.request.FormulationRequest;
import com.agriva.feeding.model.dto.response.FormulationResponse;
import com.agriva.feeding.model.entity.Formulation;
import com.agriva.feeding.model.entity.FormulationLine;
import com.agriva.feeding.model.entity.Ingredient;
import com.agriva.feeding.model.entity.IngredientStock;
import com.agriva.feeding.repository.*;
import com.agriva.feeding.service.FormulationService;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FormulationServiceImpl implements FormulationService {

    private final FeedFormulationCalculator calculator;
    private final FormulationRepository formulationRepository;
    private final FormulationLineRepository lineRepository;
    private final IngredientRepository ingredientRepository;
    private final IngredientStockRepository stockRepository;
    private final FormulationTemplateRepository templateRepository;
    private final FormulationMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public FormulationResponse createFormulation(FormulationRequest request,
                                                 UUID farmId, UUID userId) {
        // Mode-specific required fields
        if (request.getMode() == FormulationMode.ALGORITHM
                && (request.getTargetQuantityKg() == null
                || request.getTargetQuantityKg().compareTo(BigDecimal.ZERO) <= 0)) {
            throw new BusinessException("targetQuantityKg is required for ALGORITHM mode");
        }
        if (request.getMode() == FormulationMode.CUSTOM
                && (request.getLines() == null || request.getLines().isEmpty())) {
            throw new BusinessException("lines[] is required for CUSTOM mode");
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

        // Load farm ingredients + stock + name map
        List<Ingredient> ingredients = ingredientRepository.findAllByFarmIdLive(farmId);
        Map<UUID, IngredientStock> stockById = stockRepository.findAllByFarmId(farmId).stream()
                .collect(Collectors.toMap(IngredientStock::getIngredientId, s -> s));
        Map<UUID, String> nameById = ingredients.stream()
                .collect(Collectors.toMap(Ingredient::getId, Ingredient::getName));

        List<AvailableIngredient> available = ingredients.stream()
                .map(i -> {
                    IngredientStock s = stockById.get(i.getId());
                    BigDecimal qty = s == null ? BigDecimal.ZERO : s.getQuantityOnHand();
                    return new AvailableIngredient(
                            i.getId(),
                            i.getName(),
                            qty,
                            i.getUnit().name(),
                            i.getCostPerUnit());
                })
                .toList();

        List<ManualLine> manualLines = request.getMode() == FormulationMode.CUSTOM
                ? request.getLines().stream()
                  .map(l -> new ManualLine(l.getIngredientId(), l.getQuantityKg()))
                  .toList()
                : List.of();

        FormulationInput input = new FormulationInput(
                farmId,
                request.getMode(),
                request.getTargetSpecies(),
                request.getTargetQuantityKg(),
                request.getTargetCpValue(),
                available,
                manualLines,
                request.getFormulaTemplateId());

        FormulationResult result = calculator.calculate(input);

        // Belt-and-braces stock validation on the calculator output
        for (FormulationLineResult line : result.lines()) {
            AvailableIngredient ai = available.stream()
                    .filter(a -> a.ingredientId().equals(line.ingredientId()))
                    .findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Calculator returned an unknown ingredientId: " + line.ingredientId()));
            if (ai.quantityOnHand().compareTo(line.quantityKg()) < 0) {
                throw new com.agriva.shared.exception.InsufficientIngredientQuantityException(
                        ai.name(), line.quantityKg(), ai.quantityOnHand(), ai.unit());
            }
        }

        // Persist header
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
                .totalKg(result.totalKg())
                .totalCost(result.totalCost())
                .algorithmVersion(result.algorithmVersion())
                .nutrientSummaryJson(serialize(result.nutrientSummary()))
                .createdByUserId(userId)
                .build();

        Formulation saved = formulationRepository.save(formulation);

        // Persist lines
        List<FormulationLine> lines = new ArrayList<>(result.lines().size());
        for (FormulationLineResult lr : result.lines()) {
            String name = nameById.get(lr.ingredientId());
            if (name == null) {
                throw new ResourceNotFoundException(
                        "Ingredient name lookup failed for " + lr.ingredientId());
            }
            BigDecimal lineCost = lr.unitCost() == null
                    ? BigDecimal.ZERO
                    : lr.unitCost().multiply(lr.quantityKg());

            lines.add(FormulationLine.builder()
                    .formulationId(saved.getId())
                    .ingredientId(lr.ingredientId())
                    .ingredientName(name)
                    .quantityKg(lr.quantityKg())
                    .percentage(lr.percentage())
                    .unitCost(lr.unitCost())
                    .lineCost(lineCost)
                    .build());
        }
        lineRepository.saveAll(lines);

        log.info("Formulation {} created (mode={}, algorithm={}, totalKg={}, lines={})",
                saved.getId(), saved.getMode(), saved.getAlgorithmVersion(),
                saved.getTotalKg(), lines.size());

        return mapper.toResponse(saved, lines);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FormulationResponse> listFormulations(UUID farmId, Pageable pageable) {
        return formulationRepository.findByFarmIdLive(farmId, pageable)
                .map(f -> mapper.toResponse(f,
                        lineRepository.findByFormulationIdOrderByPercentageDesc(f.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public FormulationResponse getFormulation(UUID id, UUID farmId) {
        Formulation f = formulationRepository.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Formulation not found with ID: " + id));
        return mapper.toResponse(f,
                lineRepository.findByFormulationIdOrderByPercentageDesc(id));
    }

    @Override
    @Transactional
    public void deleteFormulation(UUID id, UUID farmId) {
        Formulation f = formulationRepository.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Formulation not found with ID: " + id));
        f.setDeletedAt(java.time.LocalDateTime.now());
        formulationRepository.save(f);
        log.info("Formulation {} soft-deleted from farm {}", id, farmId);
    }

    private String serialize(Map<String, BigDecimal> summary) {
        if (summary == null || summary.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            log.warn("Could not serialize nutrient summary: {}", e.getMessage());
            return null;
        }
    }
}