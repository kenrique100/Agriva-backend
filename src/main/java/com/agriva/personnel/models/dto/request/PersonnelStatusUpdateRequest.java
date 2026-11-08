package com.agriva.personnel.models.dto.request;

import com.agriva.personnel.models.enums.PersonnelStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDate;

@Data
public class PersonnelStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private PersonnelStatus status;

    /** Required for ON_LEAVE. */
    private LocalDate leaveStartDate;

    /** Required for ON_LEAVE. */
    private LocalDate expectedReturnDate;

    /** Required for SUSPENDED. ISO-8601 (e.g. PT72H). */
    private Duration suspensionDuration;
}