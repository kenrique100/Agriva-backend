package com.agriva.finance.model.dto.response;

import com.agriva.shared.models.enums.Currency;
import com.agriva.finance.model.enums.IncomeCategory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncomeResponse {
    private UUID id;
    private UUID farmId;
    private UUID transactionId;

    private String reason;
    private int quantity;
    private BigDecimal amountReceived;
    private BigDecimal expectedAmount;
    private BigDecimal dueBalance;
    private String receipt;

    private Currency currency;
    private IncomeCategory category;
    private LocalDate incomeDate;

    private UUID createdByUserId;
    private String createdByUsername;
    private String createdByEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}