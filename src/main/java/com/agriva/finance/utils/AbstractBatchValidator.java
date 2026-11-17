package com.agriva.finance.utils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared validation rules for income and expense request DTOs. Collects
 * <em>all</em> errors (never short-circuits) because the batch upload response
 * must report every problem for a row in one shot.
 *
 * <p>Labels differ by domain so user-facing messages stay domain-accurate
 * ("Amount paid" for expense vs "Amount received" for income) while the
 * structural rules live here once.
 */
public abstract class AbstractBatchValidator<R> {

    public List<String> validate(R request, UUID farmId) {
        if (request == null) {
            return List.of("Request payload is empty");
        }

        List<String> errors = new ArrayList<>();

        if (getQuantity(request) < 1) {
            errors.add("Quantity must be at least 1");
        }
        if (getExpectedAmount(request) == null
                || getExpectedAmount(request).compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Expected amount must be positive");
        }
        if (getAmount(request) == null
                || getAmount(request).compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(amountLabel() + " must be positive");
        }
        if (getDate(request) == null || getDate(request).isAfter(LocalDate.now())) {
            errors.add(dateLabel() + " cannot be in the future");
        }

        if (farmId != null
                && getAmount(request) != null
                && getDate(request) != null
                && getReason(request) != null
                && duplicateExists(farmId, getAmount(request), getDate(request), getReason(request))) {
            errors.add("Duplicate " + domainLabel()
                    + ": same amount, date and reason already exists for this farm");
        }

        return errors;
    }

    protected abstract int getQuantity(R request);
    protected abstract BigDecimal getAmount(R request);
    protected abstract BigDecimal getExpectedAmount(R request);
    protected abstract LocalDate getDate(R request);
    protected abstract String getReason(R request);

    protected abstract boolean duplicateExists(UUID farmId,
                                               BigDecimal amount,
                                               LocalDate date,
                                               String reason);

    /** "Amount paid" / "Amount received" — the amount's user-facing label. */
    protected abstract String amountLabel();

    /** "Expense date" / "Income date" — the date's user-facing label. */
    protected abstract String dateLabel();

    /** "expense" / "income" — used in the duplicate-error message. */
    protected abstract String domainLabel();
}