package com.agriva.task.model.dto.requests;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TaskValidationRequest {
    @NotNull
    private Boolean approve;
}