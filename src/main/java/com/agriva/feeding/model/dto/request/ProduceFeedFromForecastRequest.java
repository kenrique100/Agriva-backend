package com.agriva.feeding.model.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;


@Data
public class ProduceFeedFromForecastRequest {

    @Size(max = 100)
    private String formulationName;

    @NotNull(message = "producedKg is required")
    @DecimalMin(value = "0.0001", message = "producedKg must be positive")
    private BigDecimal producedKg;

    @PastOrPresent(message = "productionDate cannot be in the future")
    private LocalDate productionDate;

    @Size(max = 500)
    private String notes;
}