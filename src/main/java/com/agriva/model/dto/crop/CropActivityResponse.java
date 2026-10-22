package com.agriva.model.dto.crop;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CropActivityResponse {
    private UUID id;
    private UUID cropCycleId;
    private String activityType;
    private LocalDate activityDate;
    private String notes;
    private UUID linkedExpenseId;
    private UUID linkedTaskId;
}