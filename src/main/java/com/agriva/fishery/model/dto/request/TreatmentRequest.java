package com.agriva.fishery.model.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TreatmentRequest {

    @NotBlank(message = "Medication name is required")
    @Size(max = 100)
    private String medicationName;

    @NotNull(message = "Dosage is required")
    @DecimalMin(value = "0.0001", message = "Dosage must be positive")
    private BigDecimal dosage;

    @NotBlank(message = "Dosage unit is required")
    @Size(max = 20)
    private String dosageUnit;

    @NotNull(message = "Administration date is required")
    @PastOrPresent(message = "Administration date cannot be in the future")
    private LocalDate administeredAt;

    @Min(value = 0, message = "Withdrawal days must be >= 0")
    private Integer withdrawalDays;

    @Size(max = 500)
    private String notes;
}