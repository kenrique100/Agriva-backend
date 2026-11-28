package com.agriva.feeding.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StoreDeltaRequest {

    @NotNull(message = "deltaKg is required")
    private BigDecimal deltaKg;

    @NotBlank(message = "reason is required")
    @Size(min = 4, max = 500, message = "reason must be 4–500 characters")
    private String reason;
}