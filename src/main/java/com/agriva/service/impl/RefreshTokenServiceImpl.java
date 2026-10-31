package com.agriva.service.impl;

import com.agriva.exception.InvalidTokenException;
import com.agriva.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final String TOKEN_PREFIX = "agriva:refresh:";
    private static final String USER_TOKENS_PREFIX = "agriva:refresh-user:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 64;

    private final StringRedisTemplate redis;

    @Value("${agriva.security.jwt.refresh-token-ttl-seconds:604800}")
    private long ttlSeconds;

    @Override
    public String issue(UUID userId) {
        String raw = generateToken();
        String hash = sha256(raw);
        Duration ttl = Duration.ofSeconds(ttlSeconds);

        redis.opsForValue().set(TOKEN_PREFIX + hash, userId.toString(), ttl);
        // Track the set of active refresh tokens per user (for revoke-all).
        redis.opsForSet().add(USER_TOKENS_PREFIX + userId, hash);
        redis.expire(USER_TOKENS_PREFIX + userId, ttl);

        return raw;
    }

    @Override
    public UUID consumeAndRotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidTokenException("Refresh token is required");
        }
        String hash = sha256(rawToken);
        String userIdStr = redis.opsForValue().get(TOKEN_PREFIX + hash);

        if (userIdStr == null) {
            throw new InvalidTokenException("Refresh token is invalid or has expired");
        }

        UUID userId;
        try { userId = UUID.fromString(userIdStr); }
        catch (IllegalArgumentException e) {
            redis.delete(TOKEN_PREFIX + hash);
            throw new InvalidTokenException("Refresh token is invalid");
        }

        // Single-use: delete immediately. Rotation happens at the controller.
        redis.delete(TOKEN_PREFIX + hash);
        redis.opsForSet().remove(USER_TOKENS_PREFIX + userId, hash);
        return userId;
    }

    @Override
    public void revokeAllForUser(UUID userId) {
        Set<String> hashes = redis.opsForSet().members(USER_TOKENS_PREFIX + userId);
        if (hashes != null) {
            hashes.forEach(h -> redis.delete(TOKEN_PREFIX + h));
            redis.delete(USER_TOKENS_PREFIX + userId);
            log.info("Revoked {} refresh token(s) for userId={}", hashes.size(), userId);
        }
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}