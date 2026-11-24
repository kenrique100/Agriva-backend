package com.agriva.farming.model.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CropRequest {
    @NotBlank
    @Size(max = 100, message = "Crop name must not exceed 100 characters")
    private String name;

    @Size(max = 100, message = "Variety must not exceed 100 characters")
    private String variety;

    @Positive(message = "Days to harvest must be positive")
    private Integer expectedDaysToHarvest;
}