package com.agriva.farming.model.dto.requests;

import com.agriva.farming.model.enums.ActivityType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class CropActivityRequest {
    @NotNull
    private ActivityType activityType;

    @NotNull
    private LocalDate activityDate;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;

    private UUID linkedExpenseId;
    private UUID linkedTaskId;
}