package com.agriva.model.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TaskActionRequest {

    @NotBlank
    @Pattern(
            regexp = "^(?i)(START|STOP|CONTINUE|SUBMIT|CANCEL)$",
            message = "Action must be START, STOP, CONTINUE, SUBMIT or CANCEL"
    )
    private String action;
}