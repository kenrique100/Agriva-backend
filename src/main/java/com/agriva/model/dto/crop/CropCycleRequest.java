package com.agriva.model.dto.crop;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;
@Data public class CropCycleRequest {
    @NotNull private UUID fieldId;
    @NotNull private UUID cropId;
    @NotNull private LocalDate plantingDate;
    private String notes;
}