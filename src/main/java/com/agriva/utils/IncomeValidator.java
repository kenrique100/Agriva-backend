package com.agriva.utils;

import com.agriva.model.dto.income.IncomeRequest;
import com.agriva.repository.IncomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IncomeValidator {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000000");
    private static final int MAX_YEARS_OLD = 10;

    private final IncomeRepository incomeRepository;

    public List<String> validate(IncomeRequest request, UUID farmId) {
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

        if (request.getIncomeDate() == null) {
            errors.add("Income date is required");
        } else {
            LocalDate today = LocalDate.now();
            if (request.getIncomeDate().isAfter(today)) {
                errors.add("Income date cannot be in the future");
            }
            if (request.getIncomeDate().isBefore(today.minusYears(MAX_YEARS_OLD))) {
                errors.add("Income date cannot be older than " + MAX_YEARS_OLD + " years");
            }
        }

        if (request.getSource() == null || request.getSource().isBlank()) {
            errors.add("Source is required");
        }

        if (request.getCurrency() != null && request.getCurrency().length() != 3) {
            errors.add("Currency must be a 3-letter ISO code");
        }

        if (farmId != null
                && request.getAmount() != null
                && request.getIncomeDate() != null
                && request.getSource() != null
                && incomeRepository.existsByFarmIdAndAmountAndIncomeDateAndSource(
                farmId, request.getAmount(), request.getIncomeDate(), request.getSource())) {
            errors.add("Duplicate income: same amount, date and source already exists for this farm");
        }

        return errors;
    }
}