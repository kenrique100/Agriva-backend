package com.agriva.model.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDetails {
    private String email;
    private String username;
    private String role;
    private UUID farmId;
    private String farmName;
}