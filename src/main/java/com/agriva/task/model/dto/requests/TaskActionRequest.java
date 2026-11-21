package com.agriva.task.model.dto.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TaskActionRequest {

    @NotBlank
    @Pattern(
            regexp = "^(?i)(START|STOP|PAUSE|RESUME|CONTINUE|SUBMIT|CANCEL)$",
            message = "Action must be START, STOP, PAUSE, RESUME, CONTINUE, SUBMIT or CANCEL"
    )
    private String action;
}