package com.agriva.feeding.model.dto.request;

import com.agriva.feeding.calculator.FormulationMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Flat line-based request. CUSTOM requires {@code lines}; ALGORITHM ignores them
 * (it builds the recipe from the template). {@code targetQuantityKg} is required
 * only for ALGORITHM — for CUSTOM the request's lines drive the total.
 */
@Data
public class FormulationRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "Target species is required")
    @Size(max = 50)
    private String targetSpecies;

    @NotNull(message = "Mode is required")
    private FormulationMode mode;

    @DecimalMin(value = "0.0001", message = "Target quantity must be positive")
    private BigDecimal targetQuantityKg;

    @DecimalMin(value = "0.01")
    @DecimalMax(value = "100")
    private BigDecimal targetCpValue;

    private UUID formulaTemplateId;

    @Valid
    private List<Line> lines;

    @Data
    public static class Line {
        @NotNull(message = "ingredientId is required")
        private UUID ingredientId;

        @NotNull(message = "quantityKg is required")
        @DecimalMin(value = "0.0001", message = "quantityKg must be positive")
        private BigDecimal quantityKg;
    }
}