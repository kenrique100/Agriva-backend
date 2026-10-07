package com.agriva.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class SubUserRegisterRequest {

    @NotBlank @Email @Size(max = 100)
    private String email;

    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9_.-]*$",
            message = "Username may only contain letters, digits, '_', '.' and '-'")
    private String username;

    @NotBlank @Size(min = 6, max = 100)
    private String password;

    @NotNull
    private UUID farmId;

    private String role = "SUB_USER";
}