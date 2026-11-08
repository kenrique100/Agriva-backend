package com.agriva.task.model.dto.requests;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class TaskCreateRequest {

    @NotNull(message = "Personnel ID is required")
    private UUID personnelId;

    @NotBlank(message = "Title is required")
    @Size(max = 100)
    private String title;

    @Size(max = 500)
    private String description;

    @NotNull(message = "Deadline is required")
    @FutureOrPresent
    private LocalDate deadline;

    @Min(1)
    private Integer expectedHours;
}