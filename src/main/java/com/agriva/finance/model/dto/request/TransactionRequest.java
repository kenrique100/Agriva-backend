package com.agriva.finance.model.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TransactionRequest {

    @Size(max = 50, message = "Transaction ref must not exceed 50 characters")
    private String transactionRef;

    private LocalDateTime occurredAt;

    @Size(max = 2000, message = "Notes must not exceed 2000 characters")
    private String notes;
}