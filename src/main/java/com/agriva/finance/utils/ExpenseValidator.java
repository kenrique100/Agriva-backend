package com.agriva.finance.utils;

import com.agriva.finance.model.dto.request.ExpenseRequest;
import com.agriva.finance.repository.ExpenseRepository;
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

    private final ExpenseRepository expenseRepository;

    public List<String> validate(ExpenseRequest request, UUID farmId) {
        List<String> errors = new ArrayList<>();
        if (request == null) return List.of("Request payload is empty");

        if (request.getQtyPurchased() < 1) {
            errors.add("Quantity must be at least 1");
        }
        if (request.getExpectedAmount() == null
                || request.getExpectedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Expected amount must be positive");
        }
        if (request.getAmountPaid() == null
                || request.getAmountPaid().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Amount paid must be positive");
        }
        if (request.getExpenseDate() == null
                || request.getExpenseDate().isAfter(LocalDate.now())) {
            errors.add("Expense date cannot be in the future");
        }

        if (farmId != null && request.getAmountPaid() != null
                && request.getExpenseDate() != null && request.getReason() != null) {
            boolean exists = expenseRepository.existsByFarmIdAndAmountPaidAndExpenseDateAndReasonLive(
                    farmId, request.getAmountPaid(), request.getExpenseDate(), request.getReason());
            if (exists) {
                errors.add("Duplicate expense: same amount, date and reason already exists for this farm");
            }
        }
        return errors;
    }
}