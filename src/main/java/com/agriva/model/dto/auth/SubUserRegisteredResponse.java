package com.agriva.model.dto.auth;

import java.util.UUID;

public record SubUserRegisteredResponse(UUID userId, String email, UUID farmId) {
}