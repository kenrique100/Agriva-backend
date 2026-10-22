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

    private final IncomeRepository incomeRepository;

    public List<String> validate(IncomeRequest request, UUID farmId) {
        List<String> errors = new ArrayList<>();
        if (request == null) return List.of("Request payload is empty");

        if (request.getQuantity() < 1) {
            errors.add("Quantity must be at least 1");
        }
        if (request.getExpectedAmount() == null || request.getExpectedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Expected amount must be positive");
        }
        if (request.getAmountReceived() == null || request.getAmountReceived().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Amount received must be positive");
        }
        if (request.getIncomeDate() == null || request.getIncomeDate().isAfter(LocalDate.now())) {
            errors.add("Income date cannot be in the future");
        }

        // Duplicate Check
        if (farmId != null && request.getAmountReceived() != null && request.getIncomeDate() != null && request.getReason() != null) {
            boolean exists = incomeRepository.existsByFarmIdAndAmountReceivedAndIncomeDateAndReason(
                    farmId, request.getAmountReceived(), request.getIncomeDate(), request.getReason());
            if (exists) {
                errors.add("Duplicate income: same amount, date and reason already exists for this farm");
            }
        }
        return errors;
    }
}