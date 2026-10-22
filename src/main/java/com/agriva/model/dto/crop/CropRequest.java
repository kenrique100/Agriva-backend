package com.agriva.model.dto.crop;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data public class CropRequest {
    @NotBlank private String name;
    private String variety;
    private Integer expectedDaysToHarvest;
}