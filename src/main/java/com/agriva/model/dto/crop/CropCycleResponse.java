package com.agriva.model.dto.crop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CropCycleResponse {
    private UUID id;
    private UUID fieldId;
    private String fieldName;
    private UUID cropId;
    private String cropName;
    private LocalDate plantingDate;
    private LocalDate expectedHarvestDate;
    private LocalDate actualHarvestDate;
    private String status;
    private String notes;
}