package com.agriva.model.dto.auth;

import com.agriva.model.entity.Gender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String email;
    private String username;
    private String role;
    private UUID farmId;

    private Gender gender;
    private LocalDate dateOfBirth;
    private String phoneNumber;
    private String address;
    private String city;
    private String state;
    private String country;
    private String postalCode;
    private String preferredCurrency;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}