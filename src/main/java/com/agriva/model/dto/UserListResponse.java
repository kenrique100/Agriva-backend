package com.agriva.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

@Data
@AllArgsConstructor
public class UserListResponse {
    private UUID userId;
    private String email;
    private String username;
    private String role;
    private UUID farmId;
}