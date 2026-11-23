package com.agriva.feeding.model.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ProductionSubmissionPayload(
        String formulationName,
        BigDecimal producedKg,
        LocalDate productionDate,
        String notes,
        UUID forecastId
) {
    /** Convenience constructor for callers that don't link a forecast. */
    public ProductionSubmissionPayload(String formulationName,
                                       BigDecimal producedKg,
                                       LocalDate productionDate,
                                       String notes) {
        this(formulationName, producedKg, productionDate, notes, null);
    }
}