package com.agriva.fishery.model.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class GrowthSampleRequest {

    @NotBlank(message = "Species is required")
    @Size(max = 50)
    private String species;

    @NotNull(message = "Average weight is required")
    @DecimalMin(value = "0.01", message = "Average weight must be positive")
    private BigDecimal averageWeightG;

    @NotNull(message = "Sample size is required")
    @Positive(message = "Sample size must be positive")
    private Integer sampleSize;

    @NotNull(message = "Sampled date is required")
    @PastOrPresent(message = "Sampled date cannot be in the future")
    private LocalDate sampledAt;
}