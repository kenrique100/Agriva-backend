package com.agriva.poultry.model.dto.request;

import com.agriva.poultry.model.enums.PoultryBatchStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BatchStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private PoultryBatchStatus status;
}