package com.agriva.identity.models.dto.request;

import com.agriva.personnel.models.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class MasterRegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    @Size(max = 100)
    private String name;

    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[A-Za-z0-9_.-]*$",
            message = "Username may only contain letters, digits, '_', '.' and '-'")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 100, message = "Password must be between 12 and 100 characters")
    private String password;

    @NotBlank(message = "Farm name is required")
    @Size(min = 2, max = 100)
    private String farmName;

    @NotNull(message = "Gender is required")
    private Gender gender;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Phone number is required")
    @Size(max = 20)
    private String phoneNumber;

    @Size(max = 15, message = "National ID must not exceed 15 characters")
    @Pattern(regexp = "^[A-Za-z0-9-]*$",
            message = "National ID may only contain letters, digits and hyphens")
    @Schema(description = "National identification number", example = "CM123456789")
    private String nationalId;

    @NotBlank(message = "Country is required") private String country;
    @NotBlank(message = "State is required") private String state;
    @NotBlank(message = "City is required") private String city;
    @NotBlank(message = "Address is required") private String address;
    @NotBlank(message = "Postal code is required") private String postalCode;
    @NotBlank(message = "Currency is required") private String currency;

    @AssertTrue(message = "You must accept the user agreement")
    private boolean userAgreement;
}