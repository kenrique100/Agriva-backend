package com.agriva.feeding.model.dto.request;

import com.agriva.feeding.port.ConsumerType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Business payload for the feeding bulk stream. The consumer is resolved by
 * DISPLAY NAME in the validator via {@link com.agriva.feeding.port.FeedConsumerPort},
 * so a feeding can be staged before its consumer exists. This is the explicit
 * "do not preload consumer names in the parser" path from the master directive.
 */
public record FeedingSubmissionPayload(
        ConsumerType consumerType,
        String consumerDisplayName,
        BigDecimal quantityKg,
        LocalDate fedAt,
        String notes
) {}