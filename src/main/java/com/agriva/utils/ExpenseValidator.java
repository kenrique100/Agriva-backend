package com.agriva.utils;

import com.agriva.model.dto.expense.ExpenseRequest;
import com.agriva.repository.ExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ExpenseValidator {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000000");
    private static final int MAX_YEARS_OLD = 10;

    private final ExpenseRepository expenseRepository;

    public List<String> validate(ExpenseRequest request, UUID farmId) {
        List<String> errors = new ArrayList<>();

        if (request == null) {
            errors.add("Request payload is empty");
            return errors;
        }

        if (request.getAmount() == null) {
            errors.add("Amount is required");
        } else {
            if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                errors.add("Amount must be positive");
            }
            if (request.getAmount().compareTo(MAX_AMOUNT) > 0) {
                errors.add("Amount cannot exceed " + MAX_AMOUNT);
            }
        }

        if (request.getExpenseDate() == null) {
            errors.add("Expense date is required");
        } else {
            LocalDate today = LocalDate.now();
            if (request.getExpenseDate().isAfter(today)) {
                errors.add("Expense date cannot be in the future");
            }
            if (request.getExpenseDate().isBefore(today.minusYears(MAX_YEARS_OLD))) {
                errors.add("Expense date cannot be older than " + MAX_YEARS_OLD + " years");
            }
        }

        if (request.getCurrency() != null && request.getCurrency().length() != 3) {
            errors.add("Currency must be a 3-letter ISO code");
        }

        if (farmId != null
                && request.getAmount() != null
                && request.getExpenseDate() != null
                && request.getVendor() != null
                && expenseRepository.existsByFarmIdAndAmountAndExpenseDateAndVendor(
                farmId, request.getAmount(), request.getExpenseDate(), request.getVendor())) {
            errors.add("Duplicate expense: same amount, date and vendor already exists for this farm");
        }

        return errors;
    }
}