package com.agriva.identity.models.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteAccountRequest {
    @NotBlank(message = "Confirmation phrase is required")
    private String confirmation;
}