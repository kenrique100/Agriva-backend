package com.agriva.feeding.service.impl;

import com.agriva.feeding.calculator.FormulationCpCalculator;
import com.agriva.feeding.events.FormulationExpenseEvent;
import com.agriva.feeding.model.entity.Formulation;
import com.agriva.feeding.model.entity.FormulationLine;
import com.agriva.feeding.model.entity.Ingredient;
import com.agriva.feeding.repository.FormulationLineRepository;
import com.agriva.feeding.repository.FormulationRepository;
import com.agriva.feeding.repository.IngredientRepository;
import com.agriva.feeding.service.FormulationExpenseService;
import com.agriva.finance.model.dto.request.ExpenseRequest;
import com.agriva.finance.model.enums.ExpenseCategory;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.models.enums.Currency;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FormulationExpenseServiceImpl implements FormulationExpenseService {

    private final FormulationRepository formulationRepository;
    private final FormulationLineRepository lineRepository;
    private final IngredientRepository ingredientRepository;
    private final FormulationCpCalculator cpCalculator;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${agriva.feeding.formulation.expense-publish.enabled:true}")
    private boolean expensePublishEnabled;

    @Value("${agriva.feeding.formulation.expense.default-currency:XAF}")
    private String defaultExpenseCurrency;

    @Value("${agriva.feeding.formulation.expense.mixed-currency-policy:STRICT}")
    private String mixedCurrencyPolicy;

    @Override
    @Transactional
    public void snapshotCpAndBookExpense(Formulation formulation,
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
            eventPublisher.publishEvent(new FormulationExpenseEvent(
                    formulation.getId(), expenseSubmissionId, farmId, userId,
                    expensePayload, false));
        }
    }

    @Override
    @Transactional
    public void scheduleReversal(Formulation formulation, UUID farmId, UUID userId) {
        if (!expensePublishEnabled
                || formulation.getTotalCost() == null
                || formulation.getTotalCost().signum() <= 0) {
            return;
        }
        UUID reversalId = UUID.randomUUID();
        Currency reversalCurrency = resolveReversalCurrency(formulation, farmId);
        String reversalPayload = buildReversalPayload(formulation, reversalCurrency);
        eventPublisher.publishEvent(new FormulationExpenseEvent(
                formulation.getId(), reversalId, farmId, userId, reversalPayload, true));
    }

    private Currency resolveReversalCurrency(Formulation f, UUID farmId) {
        List<FormulationLine> lines = lineRepository
                .findByFormulationIdOrderByPercentageDesc(f.getId());
        if (lines.isEmpty()) {
            log.warn("Reversal currency: no lines for formulation {} — falling back to {}",
                    f.getId(), defaultExpenseCurrency);
            return parseDefaultCurrency();
        }

        Map<UUID, Currency> currencyById = ingredientRepository.findAllByFarmIdLive(farmId).stream()
                .filter(i -> i.getCurrency() != null)
                .collect(Collectors.toMap(
                        Ingredient::getId, Ingredient::getCurrency, (a, b) -> a));

        try {
            return resolveExpenseCurrency(lines, currencyById);
        } catch (BusinessException e) {
            log.warn("Reversal currency: mixed currencies on formulation {} — falling back to {}",
                    f.getId(), defaultExpenseCurrency);
            return parseDefaultCurrency();
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
                            + "policy=FIRST.",
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

    private String buildReversalPayload(Formulation formulation, Currency currency) {
        ExpenseRequest req = new ExpenseRequest();
        req.setReason("Reversal: Formulation deleted - " + formulation.getName());
        req.setExpenseDate(LocalDate.now());
        req.setQtyPurchased(1);
        req.setAmountPaid(formulation.getTotalCost().negate());
        req.setExpectedAmount(formulation.getTotalCost().negate());
        req.setCurrency(currency);
        req.setCategory(ExpenseCategory.ANIMAL_FEED);
        try {
            return objectMapper.writeValueAsString(req);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize Reversal ExpenseRequest", e);
        }
    }
}