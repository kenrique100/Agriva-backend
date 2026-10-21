package com.agriva.model.dto.personnel;

import com.agriva.model.entity.Department;
import com.agriva.model.entity.Gender;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class PersonnelUpdateRequest {

    @Size(min = 2, max = 100)
    private String name;

    private Gender gender;

    @Past
    private LocalDate dateOfBirth;

    @Email
    @Size(max = 100)
    private String email;

    @Pattern(regexp = "^[0-9]{9,15}$")
    private String nationalId;

    @Size(max = 20)
    private String phoneNumber;

    private Department department;

    @PastOrPresent
    private LocalDate dateOfEmployment;
}