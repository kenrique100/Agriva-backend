package com.agriva.model.dto.auth;

import com.agriva.model.entity.enums.Department;
import com.agriva.model.entity.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SubUserRegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 100)
    private String email;

    @Size(max = 100)
    private String name;

    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[A-Za-z0-9_.-]*$")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 100, message = "Password must be between 12 and 100 characters")
    private String password;

    @NotNull(message = "Gender is required")
    private Gender gender;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    @NotBlank(message = "Address is required")
    private String address;

    @Schema(description = "Department for the new sub-user")
    private Department department;
}