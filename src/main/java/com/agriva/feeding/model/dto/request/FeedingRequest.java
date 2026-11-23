package com.agriva.feeding.model.dto.request;

import com.agriva.feeding.port.ConsumerType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class FeedingRequest {

    @NotNull(message = "consumerType is required")
    private ConsumerType consumerType;

    @NotNull(message = "consumerId is required")
    private UUID consumerId;

    private UUID feedBatchId;

    @NotNull(message = "quantityKg is required")
    @DecimalMin(value = "0.0001", message = "quantityKg must be positive")
    private BigDecimal quantityKg;

    @NotNull(message = "fedAt is required")
    @PastOrPresent(message = "fedAt cannot be in the future")
    private LocalDate fedAt;

    @Size(max = 500)
    private String notes;
}