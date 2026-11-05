package com.agriva.finance.model.dto.request;

import com.agriva.shared.models.enums.Currency;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class InvestmentRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", message = "Amount must be positive")
    @Digits(integer = 15, fraction = 4, message = "Amount must have at most 15 integer digits and 4 decimals")
    private BigDecimal amount;

    @NotNull(message = "Currency is required")
    private Currency currency = Currency.USD;

    @NotBlank(message = "Investor name is required")
    @Size(max = 100, message = "Investor name must not exceed 100 characters")
    private String investorName;

    @Size(max = 50, message = "Type must not exceed 50 characters")
    private String type;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @NotNull(message = "Investment date is required")
    private LocalDate investmentDate;

    private UUID transactionId;

    private boolean activate = false;
}