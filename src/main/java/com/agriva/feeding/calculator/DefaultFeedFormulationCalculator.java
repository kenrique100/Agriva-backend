package com.agriva.feeding.calculator;

import com.agriva.feeding.model.entity.FormulationTemplate;
import com.agriva.feeding.model.entity.FormulationTemplateLine;
import com.agriva.feeding.model.enums.IngredientUnit;
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

/**
 * Default calculator shipped with DR-3. Handles BOTH formulation modes:
 * <ul>
 *   <li><b>ALGORITHM</b> — reads a {@link FormulationTemplate} (defaults to the
 *       seeded {@code AGRIVA_DEFAULT_TILAPIA_GROWER}), resolves each template
 *       line to a farm ingredient by name, and scales the template percentages
 *       to the requested {@code targetQuantityKg}. Requires every template
 *       ingredient to be present in the farm's provender (else 422).</li>
 *   <li><b>CUSTOM</b> — passes the caller's {@code manualLines} through, computes
 *       percentages and cost from the farm's cost-per-unit. Validates stock.</li>
 * </ul>
 * <p>
 * Volumetric ingredients (unit = L) are converted from weight-percentage to
 * volume using {@code Ingredient.densityKgPerL} if set. If it is unset, the
 * calculator defaults to <b>0.90 kg/L</b> — which matches Palm Oil, the only
 * liquid in the seed recipe.
 * <p>
 * The user's own calculator can override this bean by annotating theirs with
 * {@code @Primary}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultFeedFormulationCalculator implements FeedFormulationCalculator {

    private static final String ALGORITHM_VERSION = "agriva-default-v1";
    private static final String DEFAULT_TEMPLATE_NAME = "AGRIVA_DEFAULT_TILAPIA_GROWER";
    private static final BigDecimal DEFAULT_LIQUID_DENSITY = new BigDecimal("0.90");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 4;

    private final FormulationTemplateRepository templateRepository;
    private final FormulationTemplateLineRepository templateLineRepository;

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

        // Farm ingredients indexed by lowercase name (calculator matches by name)
        Map<String, AvailableIngredient> byLowerName = input.availableIngredients().stream()
                .collect(Collectors.toMap(
                        ai -> ai.name().toLowerCase(Locale.ROOT),
                        ai -> ai,
                        (a, b) -> a));

        List<FormulationLineResult> lines = new ArrayList<>(templateLines.size());
        BigDecimal totalKg = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal weightedCp = BigDecimal.ZERO;

        for (FormulationTemplateLine tl : templateLines) {
            AvailableIngredient ai = byLowerName.get(tl.getIngredientName().toLowerCase(Locale.ROOT));
            if (ai == null) {
                throw new InsufficientIngredientQuantityException(
                        tl.getIngredientName(),
                        BigDecimal.ZERO, BigDecimal.ZERO, "KG");
            }

            BigDecimal percentage = tl.getPercentage();
            BigDecimal weightEquivalentKg = input.targetQuantityKg()
                    .multiply(percentage)
                    .divide(HUNDRED, SCALE, RoundingMode.HALF_UP);

            // Volumetric conversion when the farm registered the ingredient in L
            BigDecimal requiredInStockUnit = toStockUnit(weightEquivalentKg, ai.unit());

            if (ai.quantityOnHand().compareTo(requiredInStockUnit) < 0) {
                throw new InsufficientIngredientQuantityException(
                        ai.name(),
                        requiredInStockUnit,
                        ai.quantityOnHand(),
                        ai.unit());
            }

            BigDecimal unitCost = ai.costPerUnit() == null ? BigDecimal.ZERO : ai.costPerUnit();
            // Cost is always computed against the stock unit (kg or L) — the
            // business books every ingredient in its own unit.
            BigDecimal lineCost = unitCost.multiply(requiredInStockUnit);

            BigDecimal cpPct = tl.getCrudeProteinPct() == null
                    ? BigDecimal.ZERO : tl.getCrudeProteinPct();
            weightedCp = weightedCp.add(cpPct.multiply(percentage));

            lines.add(new FormulationLineResult(
                    ai.ingredientId(),
                    requiredInStockUnit,
                    percentage,
                    unitCost));

            // totalKg is a *weight* total — used for the recipe header.
            totalKg = totalKg.add(weightEquivalentKg);
            totalCost = totalCost.add(lineCost);
        }

        BigDecimal averageCp = weightedCp.divide(HUNDRED, 2, RoundingMode.HALF_UP);
        Map<String, BigDecimal> nutrients = Map.of("crudeProteinPct", averageCp);

        log.info("ALGORITHM formulation computed: template={} lines={} totalKg={} avgCp={}",
                template.getName(), lines.size(), totalKg, averageCp);

        return new FormulationResult(lines, totalKg, totalCost, nutrients, ALGORITHM_VERSION);
    }

    private FormulationTemplate resolveTemplate(FormulationInput input) {
        if (input.formulaTemplateId() != null) {
            return templateRepository
                    .findByIdOrSystemForFarm(input.formulaTemplateId(), input.farmId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Formulation template not found: " + input.formulaTemplateId()));
        }
        return templateRepository.findSystemTemplatesLive().stream()
                .filter(t -> DEFAULT_TEMPLATE_NAME.equals(t.getName()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Default template '" + DEFAULT_TEMPLATE_NAME + "' is not seeded"));
    }

    private FormulationResult calculateCustom(FormulationInput input) {
        if (input.manualLines() == null || input.manualLines().isEmpty()) {
            throw new BusinessException("CUSTOM formulation requires at least one line");
        }

        Map<UUID, AvailableIngredient> byId = input.availableIngredients().stream()
                .collect(Collectors.toMap(AvailableIngredient::ingredientId, ai -> ai));

        List<RawLine> raw = new ArrayList<>(input.manualLines().size());
        BigDecimal totalKg = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (ManualLine ml : input.manualLines()) {
            if (ml.quantityKg() == null || ml.quantityKg().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("Line quantity must be positive for ingredient " + ml.ingredientId());
            }
            AvailableIngredient ai = byId.get(ml.ingredientId());
            if (ai == null) {
                throw new ResourceNotFoundException(
                        "Ingredient not found on this farm: " + ml.ingredientId());
            }
            BigDecimal requestedInStockUnit = toStockUnit(ml.quantityKg(), ai.unit());
            if (ai.quantityOnHand().compareTo(requestedInStockUnit) < 0) {
                throw new InsufficientIngredientQuantityException(
                        ai.name(), requestedInStockUnit, ai.quantityOnHand(), ai.unit());
            }
            BigDecimal unitCost = ai.costPerUnit() == null ? BigDecimal.ZERO : ai.costPerUnit();
            BigDecimal lineCost = unitCost.multiply(requestedInStockUnit);
            raw.add(new RawLine(ai.ingredientId(), requestedInStockUnit, unitCost, lineCost));
            totalKg = totalKg.add(ml.quantityKg());
            totalCost = totalCost.add(lineCost);
        }

        if (totalKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("CUSTOM formulation total must be positive");
        }

        List<FormulationLineResult> lines = new ArrayList<>(raw.size());
        for (RawLine r : raw) {
            BigDecimal pct = r.quantityKg()
                    .multiply(HUNDRED)
                    .divide(totalKg, SCALE, RoundingMode.HALF_UP);
            lines.add(new FormulationLineResult(
                    r.ingredientId(), r.quantityKg(), pct, r.unitCost()));
        }

        log.info("CUSTOM formulation computed: lines={} totalKg={} totalCost={}",
                lines.size(), totalKg, totalCost);

        return new FormulationResult(lines, totalKg, totalCost, Map.of(), ALGORITHM_VERSION);
    }

    /**
     * If the farm registered the ingredient with unit = L, we must convert the
     * weight-equivalent into volume using the ingredient's density (default 0.90).
     * All other units (KG, EACH) pass through unchanged.
     */
    private BigDecimal toStockUnit(BigDecimal weightEquivalentKg, String unit) {
        IngredientUnit u = IngredientUnit.valueOf(unit);
        if (u == IngredientUnit.L) {
            return weightEquivalentKg.divide(DEFAULT_LIQUID_DENSITY, SCALE, RoundingMode.HALF_UP);
        }
        return weightEquivalentKg;
    }

    private record RawLine(UUID ingredientId, BigDecimal quantityKg,
                           BigDecimal unitCost, BigDecimal lineCost) {}
}