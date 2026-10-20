package com.agriva.model.dto.expense;

import com.agriva.model.entity.Currency;
import com.agriva.model.entity.ExpenseCategory;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class ExpenseRequest {
    @NotBlank(message = "Reason is required")
    private String reason;

    @NotNull(message = "Expense date is required")
    private LocalDate expenseDate;

    @Min(value = 1, message = "Quantity must be at least 1")
    private int qtyPurchased = 1;

    @NotNull(message = "Amount paid is required")
    @Positive(message = "Amount paid must be positive")
    private BigDecimal amountPaid;

    @NotNull(message = "Expected amount is required")
    @Positive(message = "Expected amount must be positive")
    private BigDecimal expectedAmount;

    private String receipt;

    @NotNull(message = "Currency is required")
    private Currency currency = Currency.XAF;

    @NotNull(message = "Category is required")
    private ExpenseCategory category;

    private UUID transactionId;
}