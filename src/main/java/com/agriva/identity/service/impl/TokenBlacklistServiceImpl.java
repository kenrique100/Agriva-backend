package com.agriva.identity.service.impl;

import com.agriva.identity.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private static final String KEY_PREFIX = "agriva:jwt-blacklist:";
    private static final String USER_REVOKE_PREFIX = "agriva:jwt-user-revoked:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void blacklist(String jti, long ttlSeconds) {
        if (jti == null || ttlSeconds <= 0) return;
        try {
            redisTemplate.opsForValue()
                    .set(KEY_PREFIX + jti, "1", Duration.ofSeconds(ttlSeconds));
            log.info("Blacklisted token jti={} for {}s", jti, ttlSeconds);
        } catch (Exception e) {
            // Redis unavailable: log and rethrow so the caller knows the
            // user is NOT actually logged out.
            log.error("Blacklist write failed for jti={}", jti, e);
            throw new IllegalStateException("Token revocation could not be persisted", e);
        }
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null) return false;
        try {
            return redisTemplate.hasKey(KEY_PREFIX + jti);
        } catch (Exception e) {
            log.warn("Blacklist lookup failed for jti={}: {}", jti, e.getMessage());
            // Fail open — degraded mode allows the token to continue working.
            // Alternative: fail closed, but that locks everyone out during a Redis outage.
            return false;
        }
    }

    @Override
    public void blacklistAllForUser(UUID userId, long maxTtlSeconds) {
        if (userId == null || maxTtlSeconds <= 0) return;
        try {
            long now = System.currentTimeMillis() / 1000L;
            redisTemplate.opsForValue().set(
                    USER_REVOKE_PREFIX + userId,
                    String.valueOf(now),
                    Duration.ofSeconds(maxTtlSeconds));
            log.info("Revoked all active tokens for userId={}", userId);
        } catch (Exception e) {
            log.error("Revoke-all failed for userId={}", userId, e);
            throw new IllegalStateException("Token revocation could not be persisted", e);
        }
    }

    @Override
    public boolean isUserRevoked(UUID userId, long tokenIssuedAtEpochSeconds) {
        if (userId == null) return false;
        try {
            String revokedAtStr = redisTemplate.opsForValue().get(USER_REVOKE_PREFIX + userId);
            if (revokedAtStr == null) return false;
            long revokedAt = Long.parseLong(revokedAtStr);
            return tokenIssuedAtEpochSeconds < revokedAt;
        } catch (Exception e) {
            log.warn("User-revoke lookup failed: {}", e.getMessage());
            return false;
        }
    }
}