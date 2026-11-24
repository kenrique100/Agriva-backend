package com.agriva.feeding.model.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdjustStoreRequest {

    @NotNull(message = "quantityOnHandKg is required")
    @DecimalMin(value = "0", message = "quantityOnHandKg must be >= 0")
    private BigDecimal quantityOnHandKg;

    /** Optional; must match the current store version when supplied. */
    private Long expectedVersion;

    @NotBlank(message = "reason is required")
    @Size(min = 4, max = 500, message = "reason must be 4–500 characters")
    private String reason;
}