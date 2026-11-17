package com.agriva.feeding.model.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Business payload for the production bulk stream. Formulation is resolved by
 * NAME in the validator, so a production can be staged before its formulation
 * exists on the farm.
 */
public record ProductionSubmissionPayload(
        String formulationName,
        BigDecimal producedKg,
        LocalDate productionDate,
        String notes
) {}