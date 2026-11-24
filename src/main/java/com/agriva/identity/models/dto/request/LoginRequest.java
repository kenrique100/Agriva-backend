package com.agriva.identity.models.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "Email or username is required")
    @Size(min = 3, max = 100, message = "Identifier must be 3–100 characters")
    @Pattern(
            regexp = "^[A-Za-z0-9._%+\\-@]+$",
            message = "Identifier contains invalid characters")
    private String emailOrUsername;

    @NotBlank(message = "Password is required")
    @Size(max = 100, message = "Password must not exceed 100 characters")
    private String password;
}