package com.agriva.identity.service;

import java.util.UUID;

public interface RefreshTokenService {

    /**
     * Issues a new refresh token. The raw value is the return of this method;
     * the SHA-256 hash is what we persist in Redis.
     */
    String issue(UUID userId);

    /**
     * Consumes the token (single-use rotation) and returns the userId.
     * Throws {@code InvalidTokenException} when the token is unknown or expired.
     */
    UUID consumeAndRotate(String rawToken);

    /** Revoke every refresh token for a user (logout-all, account deletion). */
    void revokeAllForUser(UUID userId);
}