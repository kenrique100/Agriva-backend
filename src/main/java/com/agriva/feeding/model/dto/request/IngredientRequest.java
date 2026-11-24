package com.agriva.feeding.model.dto.request;

import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.shared.models.enums.Currency;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class IngredientRequest {

    @NotBlank(message = "Ingredient name is required")
    @Size(max = 100)
    private String name;

    @NotNull(message = "Unit is required")
    private IngredientUnit unit;

    @DecimalMin(value = "0", message = "Cost per unit must be >= 0")
    private BigDecimal costPerUnit;

    private Currency currency;

    @Size(max = 200)
    private String supplier;

    @DecimalMin(value = "0.0001", message = "Density must be positive")
    private BigDecimal densityKgPerL;

    @DecimalMin(value = "0.0", message = "Crude protein cannot be negative")
    @DecimalMax(value = "100.0", message = "Crude protein cannot exceed 100")
    private BigDecimal crudeProteinPct;
}