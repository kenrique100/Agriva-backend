package com.agriva.poultry.model.dto.request;

import com.agriva.poultry.model.enums.PoultrySpecies;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PoultryBatchRequest {

    @NotNull(message = "Species is required")
    private PoultrySpecies species;

    @NotNull(message = "Initial count is required")
    @Positive(message = "Initial count must be greater than zero")
    private Long initialCount;

    @NotNull(message = "Arrival date is required")
    @PastOrPresent(message = "Arrival date cannot be in the future")
    private LocalDate arrivalDate;

    @Size(max = 30)
    private String stage;

    @DecimalMin(value = "0.01", message = "Average weight must be positive")
    private BigDecimal averageWeightG;
}