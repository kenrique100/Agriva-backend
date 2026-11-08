package com.agriva.shared.models.ref;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserRef(
        UUID id,
        String email,
        String username,
        String role,
        UUID farmId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}