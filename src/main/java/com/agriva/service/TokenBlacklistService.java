package com.agriva.service;

public interface TokenBlacklistService {

    /**
     * Adds a JWT ID (jti) to the blacklist for the given TTL.
     * The entry expires automatically when the token would have expired anyway.
     *
     * @param jti        JWT ID claim value
     * @param ttlSeconds seconds until the token's natural expiration
     */
    void blacklist(String jti, long ttlSeconds);

    /**
     * @return true if the jti is currently blacklisted
     */
    boolean isBlacklisted(String jti);
}