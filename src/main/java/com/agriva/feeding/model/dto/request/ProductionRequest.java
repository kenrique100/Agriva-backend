package com.agriva.feeding.model.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class ProductionRequest {

    @NotNull(message = "Formulation ID is required")
    private UUID formulationId;

    @NotNull(message = "Produced kg is required")
    @DecimalMin(value = "0.0001", message = "Produced kg must be positive")
    private BigDecimal producedKg;

    @NotNull(message = "Production date is required")
    @PastOrPresent(message = "Production date cannot be in the future")
    private LocalDate productionDate;

    @Size(max = 500)
    private String notes;
}