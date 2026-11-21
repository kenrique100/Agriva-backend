package com.agriva.finance.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private UUID id;
    private UUID farmId;
    private String transactionRef;
    private LocalDateTime occurredAt;
    private String status;
    private String notes;
    private UUID createdByUserId;
    private String createdByUsername;
    private String createdByRole;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private BigDecimal totalAmount;
    private String primaryType;
}