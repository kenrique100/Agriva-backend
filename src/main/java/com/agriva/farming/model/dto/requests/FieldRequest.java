package com.agriva.farming.model.dto.requests;

import com.agriva.farming.model.enums.SoilType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FieldRequest {
    @NotBlank
    private String name;
    private BigDecimal sizeHectares;
    private SoilType soilType;
}