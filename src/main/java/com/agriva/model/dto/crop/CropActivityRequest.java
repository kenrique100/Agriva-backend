package com.agriva.model.dto.crop;

import com.agriva.model.entity.enums.ActivityType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;
@Data public class CropActivityRequest {
    @NotNull private ActivityType activityType;
    @NotNull private LocalDate activityDate;
    private String notes;
    private UUID linkedExpenseId;
    private UUID linkedTaskId;
}