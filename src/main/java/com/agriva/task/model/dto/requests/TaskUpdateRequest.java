package com.agriva.task.model.dto.requests;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class TaskUpdateRequest {

    @Size(max = 100)
    private String title;

    @Size(max = 500)
    private String description;

    private LocalDate deadline;

    @Min(1)
    private Integer expectedHours;

    private UUID personnelId;
}