package com.agriva.fishery.model.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PondRequest {

    @NotBlank(message = "Pond name is required")
    @Size(max = 100, message = "Pond name must not exceed 100 characters")
    private String name;

    @Size(max = 255)
    private String location;

    @DecimalMin(value = "0.01", message = "Surface area must be positive")
    private BigDecimal surfaceAreaM2;

    @DecimalMin(value = "0.01", message = "Depth must be positive")
    private BigDecimal depthM;
}