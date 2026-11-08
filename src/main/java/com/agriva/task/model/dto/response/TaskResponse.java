package com.agriva.task.model.dto.response;

import com.agriva.task.model.enums.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {
    private UUID id;
    private UUID farmId;
    private UUID personnelId;
    private String personnelName;
    private String title;
    private String description;
    private LocalDate deadline;
    private TaskStatus status;
    private Integer expectedHours;
    private Double actualHours;
    private Long totalWorkedMinutes;
    private LocalDateTime startTime;
    private LocalDateTime stopTime;
    private LocalDateTime lastResumeTime;
    private Boolean isValidated;
    private LocalDateTime validationTime;
    private Boolean isSubmitted;
    private UUID assignedByUserId;
    private UUID validatedByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}