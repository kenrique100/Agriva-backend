package com.agriva.model.dto.investment;

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
public class InvestmentResponse {
    private UUID id;
    private UUID farmId;
    private UUID transactionId;
    private BigDecimal amount;
    private String currency;
    private String investorName;
    private String type;
    private String description;
    private LocalDate investmentDate;
    private UUID createdByUserId;
    private String createdByUsername;
    private String createdByEmail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}