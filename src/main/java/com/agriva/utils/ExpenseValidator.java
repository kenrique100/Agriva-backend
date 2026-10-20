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

    private final ExpenseRepository expenseRepository;

    public List<String> validate(ExpenseRequest request, UUID farmId) {
        List<String> errors = new ArrayList<>();
        if (request == null) return List.of("Request payload is empty");

        if (request.getAmountPaid() == null || request.getAmountPaid().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Amount paid must be positive");
        }
        if (request.getExpenseDate() == null || request.getExpenseDate().isAfter(LocalDate.now())) {
            errors.add("Expense date cannot be in the future");
        }

        // Duplicate Check
        if (farmId != null && request.getAmountPaid() != null && request.getExpenseDate() != null && request.getReason() != null) {
            boolean exists = expenseRepository.existsByFarmIdAndAmountPaidAndExpenseDateAndReason(
                    farmId, request.getAmountPaid(), request.getExpenseDate(), request.getReason());
            if (exists) {
                errors.add("Duplicate expense: same amount, date and reason already exists for this farm");
            }
        }
        return errors;
    }
}