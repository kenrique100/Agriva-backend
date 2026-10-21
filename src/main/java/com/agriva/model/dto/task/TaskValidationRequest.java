package com.agriva.model.dto.task;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TaskValidationRequest {
    @NotNull
    private Boolean approve;
}