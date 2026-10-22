package com.agriva.model.dto.crop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FieldResponse {
    private UUID id;
    private String name;
    private BigDecimal sizeHectares;
    private String soilType;
}