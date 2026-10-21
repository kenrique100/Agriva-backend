package com.agriva.model.dto.personnel;

import com.agriva.model.entity.Department;
import com.agriva.model.entity.Gender;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PersonnelCreateRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100)
    private String name;

    @NotNull(message = "Gender is required")
    private Gender gender;

    @NotNull(message = "Date of birth is required")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @Email(message = "Invalid email")
    @Size(max = 100)
    private String email;

    @NotBlank(message = "National ID is required")
    @Pattern(regexp = "^[0-9]{9,15}$", message = "National ID must be 9-15 digits")
    private String nationalId;

    @Size(max = 20)
    private String phoneNumber;

    @NotNull(message = "Department is required")
    private Department department;

    @NotNull(message = "Date of employment is required")
    @PastOrPresent(message = "Date of employment must be past or present")
    private LocalDate dateOfEmployment;

    /** If true, a User (role=SUB_USER) is created alongside this Personnel record. */
    private boolean createLogin = false;

    /** Required when createLogin is true. */
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[A-Za-z0-9._-]*$")
    private String username;

    /** Required when createLogin is true. */
    @Size(min = 8, max = 100)
    private String password;
}