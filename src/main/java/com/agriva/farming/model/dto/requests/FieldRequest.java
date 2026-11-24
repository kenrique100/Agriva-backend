package com.agriva.farming.model.dto.requests;

import com.agriva.farming.model.enums.SoilType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class FieldRequest {
    @NotBlank
    @Size(max = 100, message = "Field name must not exceed 100 characters")
    private String name;

    private BigDecimal sizeHectares;

    private SoilType soilType;
}