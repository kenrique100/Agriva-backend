package com.agriva.payroll.model.dto.requests;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class SalaryPaymentRequest {

    @NotNull(message = "Personnel ID is required")
    private UUID personnelId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", message = "Amount must be positive")
    @Digits(integer = 15, fraction = 4)
    private BigDecimal amount;

    @NotNull(message = "Payment date is required")
    @PastOrPresent
    private LocalDate paymentDate;

    @Size(max = 500)
    private String notes;
}