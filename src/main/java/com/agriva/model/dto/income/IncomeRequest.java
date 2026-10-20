package com.agriva.model.dto.income;

import com.agriva.model.entity.Currency;
import com.agriva.model.entity.IncomeCategory;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class IncomeRequest {
    @NotBlank(message = "Reason is required")
    private String reason;

    @NotNull(message = "Income date is required")
    private LocalDate incomeDate;

    @Min(value = 1, message = "Quantity must be at least 1")
    private int quantity = 1;

    @NotNull(message = "Amount received is required")
    @Positive(message = "Amount received must be positive")
    private BigDecimal amountReceived;

    @NotNull(message = "Expected amount is required")
    @Positive(message = "Expected amount must be positive")
    private BigDecimal expectedAmount;

    private String receipt;

    @NotNull(message = "Currency is required")
    private Currency currency = Currency.XAF;

    @NotNull(message = "Category is required")
    private IncomeCategory category;

    private UUID transactionId;
}