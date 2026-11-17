package com.agriva.poultry.model.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class EggRecordRequest {

    @NotNull(message = "Eggs collected is required")
    @Min(value = 0, message = "Eggs collected must be >= 0")
    private Integer eggsCollected;

    @Min(value = 0, message = "Broken eggs must be >= 0")
    private Integer brokenEggs = 0;

    @NotNull(message = "Recorded date is required")
    @PastOrPresent(message = "Recorded date cannot be in the future")
    private LocalDate recordedAt;
}