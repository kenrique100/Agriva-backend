package com.agriva.feeding.model.dto.request;

import com.agriva.feeding.port.ConsumerType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Records one physical feed delivery.
 * <p>
 * {@code feedBatchId} is optional: the farm store is a single fungible
 * balance, and the feeding can be recorded without batch traceability. When
 * supplied, the batch must exist on this farm (else 404).
 */
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