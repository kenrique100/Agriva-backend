package com.agriva.service.impl;

import com.agriva.security.JwtUtil;
import com.agriva.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private static final String KEY_PREFIX = "agriva:jwt-blacklist:";

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;

    @Override
    public void blacklist(String token) {
        try {
            Date expiration = jwtUtil.extractExpiration(token);
            long ttlMillis = expiration.getTime() - System.currentTimeMillis();

            if (ttlMillis <= 0) {
                log.debug("Token already expired — no need to blacklist");
                return;
            }

            redisTemplate.opsForValue()
                    .set(KEY_PREFIX + token, "1", Duration.ofMillis(ttlMillis));

            log.info("Blacklisted token until {}", expiration);
        } catch (Exception e) {
            log.warn("Failed to blacklist token: {}", e.getMessage());
        }
    }

    @Override
    public boolean isBlacklisted(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
        } catch (Exception e) {
            log.warn("Blacklist lookup failed: {}", e.getMessage());
            return false;
        }
    }
}