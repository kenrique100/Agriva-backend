package com.agriva.feeding.model.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class DemandForecastRequest {

    @NotBlank(message = "Species is required")
    @Size(max = 50)
    private String species;

    @NotNull(message = "Number of head is required")
    @Positive(message = "Number of head must be positive")
    @Max(value = Integer.MAX_VALUE, message = "Number of head is too large")
    private Long numberOfHead;

    @NotNull(message = "Initial weight is required")
    @DecimalMin(value = "0.0001", message = "Initial weight must be positive")
    private BigDecimal initialWeightKg;

    @NotNull(message = "Target weight is required")
    @DecimalMin(value = "0.0001", message = "Target weight must be positive")
    private BigDecimal targetWeightKg;

    @DecimalMin(value = "1", message = "Water temperature must be >= 1°C")
    @DecimalMax(value = "45", message = "Water temperature must be <= 45°C")
    private BigDecimal waterTemperatureC;

    @DecimalMin(value = "0.01", message = "Target CP must be positive")
    @DecimalMax(value = "100", message = "Target CP must be <= 100")
    private BigDecimal targetCpValue;

    private UUID formulaTemplateId;
}