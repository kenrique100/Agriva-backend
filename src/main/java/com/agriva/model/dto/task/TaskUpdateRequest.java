package com.agriva.model.dto.task;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class TaskUpdateRequest {
    @Size(max = 100)
    private String title;

    @Size(max = 500)
    private String description;

    @FutureOrPresent
    private LocalDate deadline;

    @Min(1)
    private Integer expectedHours;

    private UUID personnelId;
}