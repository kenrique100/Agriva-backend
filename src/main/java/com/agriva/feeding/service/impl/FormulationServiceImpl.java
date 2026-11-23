package com.agriva.feeding.service.impl;

import com.agriva.feeding.calculator.*;
import com.agriva.feeding.events.FormulationExpenseEvent;
import com.agriva.feeding.mapper.FormulationMapper;
import com.agriva.feeding.model.dto.request.FormulationRequest;
import com.agriva.feeding.model.dto.response.FormulationResponse;
import com.agriva.feeding.model.entity.Formulation;
import com.agriva.feeding.model.entity.FormulationLine;
import com.agriva.feeding.model.entity.Ingredient;
import com.agriva.feeding.model.entity.IngredientStock;
import com.agriva.feeding.repository.*;
import com.agriva.feeding.service.FormulationService;
import com.agriva.feeding.utils.IngredientCrudeProteinResolver;
import com.agriva.finance.model.dto.request.ExpenseRequest;
import com.agriva.finance.model.enums.ExpenseCategory;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.ResourceNotFoundException;
import com.agriva.shared.models.enums.Currency;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FormulationServiceImpl implements FormulationService {

    private final FeedFormulationCalculator calculator;
    private final FormulationCpCalculator cpCalculator;
    private final FormulationRepository formulationRepository;
    private final FormulationLineRepository lineRepository;
    private final IngredientRepository ingredientRepository;
    private final IngredientStockRepository stockRepository;
    private final FormulationTemplateRepository templateRepository;
    private final DemandForecastRepository demandForecastRepository;
    private final FormulationMapper mapper;
    private final ObjectMapper objectMapper;
    private final IngredientCrudeProteinResolver cpResolver;
    private final ApplicationEventPublisher eventPublisher;    // Stage 6

    @Value("${agriva.feeding.formulation.expense-publish.enabled:true}")
    private boolean expensePublishEnabled;

    @Value("${agriva.feeding.formulation.expense.default-currency:XAF}")
    private String defaultExpenseCurrency;

    @Value("${agriva.feeding.formulation.expense.mixed-currency-policy:STRICT}")
    private String mixedCurrencyPolicy;

    @Override
    @Transactional
    public FormulationResponse createFormulation(FormulationRequest request,
                                                 UUID farmId, UUID userId) {
        if (formulationRepository.findMostRecentByFarmIdAndNameLive(farmId, request.getName()).isPresent()) {
            throw new BusinessException(
                    "A formulation with the name '" + request.getName() + "' already exists on this farm");
        }

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
                            + "accept explicit ingredient lines. For Excel uploads leave "
                            + "'ingredient_name' and 'quantity_kg' blank. Use CUSTOM mode "
                            + "if you want to specify ingredients manually.");
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

        List<Ingredient> ingredients = ingredientRepository.findAllByFarmIdLive(farmId);
        Map<UUID, IngredientStock> stockById = stockRepository.findAllByFarmId(farmId).stream()
                .collect(Collectors.toMap(IngredientStock::getIngredientId, s -> s));
        Map<UUID, String> nameById = ingredients.stream()
                .collect(Collectors.toMap(Ingredient::getId, Ingredient::getName));
        Map<UUID, Currency> currencyById = ingredients.stream()
                .filter(i -> i.getCurrency() != null)
                .collect(Collectors.toMap(Ingredient::getId, Ingredient::getCurrency, (a, b) -> a));

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

        Formulation saved = formulationRepository.save(formulation);

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
                    .quantityKg(lr.quantityInStockUnit())
                    .percentage(lr.percentage())
                    .unitCost(lr.unitCost())
                    .lineCost(lineCost)
                    .crudeProteinPct(cpById.get(lr.ingredientId()))
                    .build());
        }
        lineRepository.saveAll(lines);

        log.info("Formulation {} created (mode={}, algorithm={}, totalKg={}, lines={}, "
                        + "forecast={}, cpResolved={}/{} lines)",
                saved.getId(), saved.getMode(), saved.getAlgorithmVersion(),
                saved.getTotalKg(), lines.size(), saved.getForecastId(),
                lines.stream().filter(l -> l.getCrudeProteinPct() != null).count(),
                lines.size());

        snapshotCpAndBookExpense(saved, lines, farmId, userId, currencyById);
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
    public void deleteFormulation(UUID id, UUID farmId, UUID userId) {
        Formulation f = formulationRepository.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Formulation not found with ID: " + id));

        f.setDeletedAt(LocalDateTime.now());
        formulationRepository.save(f);

        if (expensePublishEnabled && f.getTotalCost() != null && f.getTotalCost().signum() > 0) {
            UUID reversalId = UUID.randomUUID();
            String reversalPayload = buildReversalPayload(f);
            // Deferred to AFTER_COMMIT by FormulationExpenseListener.
            eventPublisher.publishEvent(new FormulationExpenseEvent(
                    f.getId(), reversalId, farmId, userId, reversalPayload, true));
        }

        log.info("Formulation {} soft-deleted from farm {} (expense reversal scheduled)", id, farmId);
    }

    private void snapshotCpAndBookExpense(Formulation formulation,
                                          List<FormulationLine> lines,
                                          UUID farmId, UUID userId,
                                          Map<UUID, Currency> currencyById) {
        List<FormulationCpCalculator.Line> cpLines = lines.stream()
                .filter(l -> l.getPercentage() != null && l.getPercentage().signum() > 0)
                .map(l -> new FormulationCpCalculator.Line(l.getPercentage(), l.getCrudeProteinPct()))
                .toList();
        BigDecimal computedCp = cpCalculator.weightedAverageCp(cpLines);
        formulation.setComputedCpValue(computedCp);

        UUID expenseSubmissionId = null;
        String expensePayload = null;
        if (expensePublishEnabled
                && formulation.getTotalCost() != null
                && formulation.getTotalCost().signum() > 0) {
            Currency expenseCurrency = resolveExpenseCurrency(lines, currencyById);
            expenseSubmissionId = UUID.randomUUID();
            formulation.setExpenseSubmissionId(expenseSubmissionId);
            expensePayload = buildExpensePayload(formulation, expenseCurrency);
        }

        formulationRepository.save(formulation);

        if (expenseSubmissionId != null) {
            // Held until commit, then handled by FormulationExpenseListener.
            eventPublisher.publishEvent(new FormulationExpenseEvent(
                    formulation.getId(), expenseSubmissionId, farmId, userId,
                    expensePayload, false));
        }
    }

    private String buildExpensePayload(Formulation formulation, Currency currency) {
        ExpenseRequest req = new ExpenseRequest();
        req.setReason("Feed formulation: " + formulation.getName());
        req.setExpenseDate(LocalDate.now());
        req.setQtyPurchased(1);
        req.setAmountPaid(formulation.getTotalCost());
        req.setExpectedAmount(formulation.getTotalCost());
        req.setCurrency(currency);
        req.setCategory(ExpenseCategory.ANIMAL_FEED);
        try {
            return objectMapper.writeValueAsString(req);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize ExpenseRequest", e);
        }
    }

    private String buildReversalPayload(Formulation formulation) {
        ExpenseRequest req = new ExpenseRequest();
        req.setReason("Reversal: Formulation deleted - " + formulation.getName());
        req.setExpenseDate(LocalDate.now());
        req.setQtyPurchased(1);
        req.setAmountPaid(formulation.getTotalCost().negate());
        req.setExpectedAmount(formulation.getTotalCost().negate());
        req.setCurrency(Currency.XAF);
        req.setCategory(ExpenseCategory.ANIMAL_FEED);
        try {
            return objectMapper.writeValueAsString(req);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize Reversal ExpenseRequest", e);
        }
    }

    private Currency resolveExpenseCurrency(List<FormulationLine> lines,
                                            Map<UUID, Currency> currencyById) {
        Set<Currency> distinctCurrencies = lines.stream()
                .map(l -> currencyById.get(l.getIngredientId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (distinctCurrencies.isEmpty()) {
            log.warn("No ingredient currency found for formulation lines — "
                    + "falling back to default currency '{}'", defaultExpenseCurrency);
            return parseDefaultCurrency();
        }

        if (distinctCurrencies.size() > 1) {
            if ("STRICT".equalsIgnoreCase(mixedCurrencyPolicy)) {
                throw new BusinessException(
                        "Mixed currencies detected in formulation: " + distinctCurrencies
                                + ". Standardise ingredient currencies before creating this "
                                + "formulation, or set "
                                + "agriva.feeding.formulation.expense.mixed-currency-policy=FIRST "
                                + "to allow the legacy pick-first behaviour.");
            }
            log.warn("Mixed currencies detected in formulation ({}). Using the first one — "
                            + "policy=FIRST. Standardise ingredient currencies to fix.",
                    distinctCurrencies);
        }

        return distinctCurrencies.iterator().next();
    }

    private Currency parseDefaultCurrency() {
        try {
            return Currency.valueOf(defaultExpenseCurrency.toUpperCase());
        } catch (Exception e) {
            log.error("Invalid default expense currency '{}', falling back to XAF",
                    defaultExpenseCurrency);
            return Currency.XAF;
        }
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
}