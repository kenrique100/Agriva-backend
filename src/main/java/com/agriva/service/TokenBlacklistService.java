package com.agriva.service;

import java.util.UUID;

public interface TokenBlacklistService {
    void blacklist(String jti, long ttlSeconds);
    boolean isBlacklisted(String jti);

    /** revoke every token previously issued to a user. */
    void blacklistAllForUser(UUID userId, long maxTtlSeconds);
    boolean isUserRevoked(UUID userId, long tokenIssuedAtEpochSeconds);
}