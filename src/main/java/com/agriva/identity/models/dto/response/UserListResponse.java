package com.agriva.identity.models.dto.response;

import com.agriva.personnel.models.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserListResponse {
    private UUID userId;
    private String email;
    private String username;
    private String role;
    private UUID farmId;
    private Gender gender;
    private String phoneNumber;
}