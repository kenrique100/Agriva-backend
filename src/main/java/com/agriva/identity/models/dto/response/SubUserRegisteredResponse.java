package com.agriva.identity.models.dto.response;

import java.util.UUID;

public record SubUserRegisteredResponse(UUID userId, String email, UUID farmId) {
}