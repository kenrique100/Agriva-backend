package com.agriva.farming.model.dto.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class CropCycleRequest {
    @NotNull
    private UUID fieldId;

    @NotNull
    private UUID cropId;

    @NotNull
    @PastOrPresent(message = "Planting date cannot be in the future")
    private LocalDate plantingDate;

    private String notes;
}