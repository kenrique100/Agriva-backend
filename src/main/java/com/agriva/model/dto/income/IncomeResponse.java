package com.agriva.model.dto.income;

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
    private BigDecimal amount;
    private String currency;
    private String source;
    private String category;
    private String description;
    private LocalDate incomeDate;
    private UUID createdByUserId;
    private String createdByUsername;
    private String createdByEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}