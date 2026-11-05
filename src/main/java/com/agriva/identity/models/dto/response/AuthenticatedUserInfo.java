package com.agriva.identity.models.dto.response;

import java.util.UUID;

public record AuthenticatedUserInfo(UUID userId, UUID farmId, String email,
                                    String username, String role) {
}