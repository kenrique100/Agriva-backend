package com.agriva.farming.model.dto.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CropRequest {
    @NotBlank
    private String name;
    private String variety;
    private Integer expectedDaysToHarvest;
}