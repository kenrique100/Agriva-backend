package com.agriva.feeding.model.dto.request;

import com.agriva.feeding.port.ConsumerType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FeedingSubmissionPayload(
        ConsumerType consumerType,
        String consumerDisplayName,
        BigDecimal quantityKg,
        LocalDate fedAt,
        String notes
) {}