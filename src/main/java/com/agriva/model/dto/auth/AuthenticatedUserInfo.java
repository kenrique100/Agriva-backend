package com.agriva.model.dto.auth;

import java.util.UUID;

public record AuthenticatedUserInfo(UUID userId, UUID farmId, String email,
                                    String username, String role) {
}