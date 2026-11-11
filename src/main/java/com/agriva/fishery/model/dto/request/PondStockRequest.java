package com.agriva.fishery.model.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PondStockRequest {

    @NotBlank(message = "Species is required")
    @Size(max = 50)
    private String species;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be greater than zero")
    private Long quantity;

    @DecimalMin(value = "0.01", message = "Average weight must be positive")
    private BigDecimal averageWeightG;

    @Size(max = 30)
    private String stage;

    @PastOrPresent(message = "Stocking date cannot be in the future")
    private LocalDate stockedAt;
}