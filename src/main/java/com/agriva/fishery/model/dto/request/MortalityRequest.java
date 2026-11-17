package com.agriva.fishery.model.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MortalityRequest {

    @NotBlank(message = "Species is required")
    @Size(max = 50)
    private String species;

    @NotNull(message = "Quantity is required")
    @Positive(message = "Quantity must be positive")
    private Long quantity;

    @NotNull(message = "Recorded date is required")
    @PastOrPresent(message = "Recorded date cannot be in the future")
    private LocalDate recordedAt;

    @Size(max = 500)
    private String suspectedCause;
}