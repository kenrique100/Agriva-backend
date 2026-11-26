package com.agriva.feeding.calculator;

import com.agriva.feeding.model.entity.FormulationTemplate;
import com.agriva.feeding.model.entity.FormulationTemplateLine;
import com.agriva.feeding.repository.FormulationTemplateLineRepository;
import com.agriva.feeding.repository.FormulationTemplateRepository;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.InsufficientIngredientQuantityException;
import com.agriva.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultFeedFormulationCalculator implements FeedFormulationCalculator {

    private static final String ALGORITHM_VERSION = "agriva-default-v1";
    private static final BigDecimal FALLBACK_LIQUID_DENSITY = new BigDecimal("0.90");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 4;

    private final FormulationTemplateRepository templateRepository;
    private final FormulationTemplateLineRepository templateLineRepository;
    private final FormulationCpCalculator cpCalculator;

    @Override
    public String algorithmVersion() {
        return ALGORITHM_VERSION;
    }

    @Override
    public Set<FormulationMode> supportedModes() {
        return EnumSet.allOf(FormulationMode.class);
    }

    @Override
    public FormulationResult calculate(FormulationInput input) {
        return switch (input.mode()) {
            case ALGORITHM -> calculateAutomatic(input);
            case CUSTOM -> calculateCustom(input);
        };
    }

    private FormulationResult calculateAutomatic(FormulationInput input) {
        if (input.targetQuantityKg() == null
                || input.targetQuantityKg().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("targetQuantityKg is required for ALGORITHM mode");
        }

        FormulationTemplate template = resolveTemplate(input);
        List<FormulationTemplateLine> templateLines =
                templateLineRepository.findByTemplateId(template.getId());
        if (templateLines.isEmpty()) {
            throw new BusinessException(
                    "Template '" + template.getName() + "' has no ingredient lines");
        }

        Map<String, AvailableIngredient> byLowerName = input.availableIngredients().stream()
                .collect(Collectors.toMap(
                        ai -> ai.name().toLowerCase(Locale.ROOT),
                        ai -> ai,
                        (a, b) -> a));

        List<FormulationLineResult> lines = new ArrayList<>(templateLines.size());
        List<FormulationCpCalculator.Line> cpLines = new ArrayList<>(templateLines.size());

        BigDecimal totalKg = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (FormulationTemplateLine tl : templateLines) {
            AvailableIngredient ai = byLowerName.get(
                    tl.getIngredientName().toLowerCase(Locale.ROOT));
            if (ai == null) {
                throw new InsufficientIngredientQuantityException(
                        tl.getIngredientName(),
                        BigDecimal.ZERO, BigDecimal.ZERO, "KG");
            }

            BigDecimal percentage = tl.getPercentage();
            BigDecimal weightEquivalentKg = input.targetQuantityKg()
                    .multiply(percentage)
                    .divide(HUNDRED, SCALE, RoundingMode.HALF_UP);

            BigDecimal requiredInStockUnit =
                    toStockUnit(weightEquivalentKg, ai.unit(), ai.densityKgPerL());

            if (ai.quantityOnHand().compareTo(requiredInStockUnit) < 0) {
                throw new InsufficientIngredientQuantityException(
                        ai.name(),
                        requiredInStockUnit,
                        ai.quantityOnHand(),
                        ai.unit());
            }

            if (ai.costPerUnit() == null) {
                throw new BusinessException(
                        "Ingredient '" + ai.name()
                                + "' has no cost per unit configured on this farm");
            }
            BigDecimal unitCost = ai.costPerUnit();
            BigDecimal lineCost = unitCost.multiply(requiredInStockUnit);

            cpLines.add(new FormulationCpCalculator.Line(percentage, ai.crudeProteinPct()));

            lines.add(new FormulationLineResult(
                    ai.ingredientId(),
                    requiredInStockUnit,
                    percentage,
                    unitCost));

            totalKg = totalKg.add(weightEquivalentKg);
            totalCost = totalCost.add(lineCost);
        }

        BigDecimal averageCp = cpCalculator.weightedAverageCp(cpLines);
        Map<String, BigDecimal> nutrients = Map.of("crudeProteinPct", averageCp);

        log.info("ALGORITHM formulation computed: template={} species={} lines={} "
                        + "totalKg={} avgCp={}%",
                template.getName(), input.targetSpecies(), lines.size(), totalKg, averageCp);

        return new FormulationResult(lines, totalKg, totalCost, nutrients, ALGORITHM_VERSION);
    }

    private FormulationTemplate resolveTemplate(FormulationInput input) {
        if (input.formulaTemplateId() != null) {
            return templateRepository
                    .findByIdOrSystemForFarm(input.formulaTemplateId(), input.farmId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Formulation template not found: " + input.formulaTemplateId()));
        }

        String defaultName = SpeciesKey.defaultTemplateNameFor(input.targetSpecies());
        return templateRepository.findSystemTemplatesLive().stream()
                .filter(t -> defaultName.equals(t.getName()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "No template specified and no default '" + defaultName
                                + "' is seeded. Seed the default for this species "
                                + "or set formulaTemplateId explicitly."));
    }

    private FormulationResult calculateCustom(FormulationInput input) {
        if (input.manualLines() == null || input.manualLines().isEmpty()) {
            throw new BusinessException("CUSTOM formulation requires at least one line");
        }

        Map<UUID, AvailableIngredient> byId = input.availableIngredients().stream()
                .collect(Collectors.toMap(
                        AvailableIngredient::ingredientId,
                        ai -> ai,
                        (a, b) -> a));

        BigDecimal totalKg = BigDecimal.ZERO;
        List<ResolvedCustomLine> resolved = new ArrayList<>(input.manualLines().size());

        for (var ml : input.manualLines()) {
            UUID ingId = ml.ingredientId();
            BigDecimal weightKg = ml.quantityKg();
            if (weightKg == null || weightKg.signum() <= 0) {
                throw new BusinessException("quantityKg must be > 0 for ingredient " + ingId);
            }

            AvailableIngredient ai = byId.get(ingId);
            if (ai == null) {
                throw new ResourceNotFoundException(
                        "Ingredient not found on this farm: " + ingId);
            }

            BigDecimal requiredInStockUnit =
                    toStockUnit(weightKg, ai.unit(), ai.densityKgPerL());

            if (ai.quantityOnHand().compareTo(requiredInStockUnit) < 0) {
                throw new InsufficientIngredientQuantityException(
                        ai.name(), requiredInStockUnit, ai.quantityOnHand(), ai.unit());
            }

            if (ai.costPerUnit() == null) {
                throw new BusinessException(
                        "Ingredient '" + ai.name()
                                + "' has no cost per unit configured on this farm");
            }

            resolved.add(new ResolvedCustomLine(ai, weightKg, requiredInStockUnit));
            totalKg = totalKg.add(weightKg);
        }

        if (totalKg.signum() <= 0) {
            throw new BusinessException("Total quantity must be greater than zero");
        }

        List<FormulationLineResult> lines = new ArrayList<>(resolved.size());
        List<FormulationCpCalculator.Line> cpLines = new ArrayList<>(resolved.size());
        BigDecimal totalCost = BigDecimal.ZERO;

        for (ResolvedCustomLine r : resolved) {
            BigDecimal percentage = r.weightKg
                    .multiply(HUNDRED)
                    .divide(totalKg, SCALE, RoundingMode.HALF_UP);

            BigDecimal lineCost = r.ai.costPerUnit().multiply(r.requiredInStockUnit);

            lines.add(new FormulationLineResult(
                    r.ai.ingredientId(),
                    r.requiredInStockUnit,
                    percentage,
                    r.ai.costPerUnit()));

            totalCost = totalCost.add(lineCost);
            cpLines.add(new FormulationCpCalculator.Line(percentage, r.ai.crudeProteinPct()));
        }

        BigDecimal averageCp = cpCalculator.weightedAverageCp(cpLines);
        Map<String, BigDecimal> nutrients = Map.of("crudeProteinPct", averageCp);

        log.info("CUSTOM formulation computed: lines={} totalKg={} totalCost={} avgCp={}%",
                lines.size(), totalKg, totalCost, averageCp);

        return new FormulationResult(lines, totalKg, totalCost, nutrients, ALGORITHM_VERSION);
    }

    private record ResolvedCustomLine(
            AvailableIngredient ai,
            BigDecimal weightKg,
            BigDecimal requiredInStockUnit
    ) {}

    private BigDecimal toStockUnit(BigDecimal weightKg,
                                   String stockUnit,
                                   BigDecimal densityKgPerL) {
        if (stockUnit != null && "L".equalsIgnoreCase(stockUnit)) {
            BigDecimal density = (densityKgPerL != null && densityKgPerL.signum() > 0)
                    ? densityKgPerL
                    : FALLBACK_LIQUID_DENSITY;
            return weightKg.divide(density, SCALE, RoundingMode.HALF_UP);
        }
        return weightKg;
    }
}