package com.agriva.model.dto.expense;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class ExpenseRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", message = "Amount must be positive")
    @Digits(integer = 15, fraction = 4, message = "Amount must have at most 15 integer digits and 4 decimals")
    private BigDecimal amount;

    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    private String currency = "USD";

    @Size(max = 50, message = "Category must not exceed 50 characters")
    private String category;

    @Size(max = 100, message = "Vendor must not exceed 100 characters")
    private String vendor;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @NotNull(message = "Expense date is required")
    private LocalDate expenseDate;

    private UUID transactionId;
}