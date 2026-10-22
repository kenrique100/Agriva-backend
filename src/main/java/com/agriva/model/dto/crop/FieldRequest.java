package com.agriva.model.dto.crop;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;
@Data public class FieldRequest {
    @NotBlank private String name;
    private BigDecimal sizeHectares;
    private String soilType;
}