package com.agriva.service.impl;

import com.agriva.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private static final String KEY_PREFIX = "agriva:jwt:blacklist:";
    private static final String VALUE = "1";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void blacklist(String jti, long ttlSeconds) {
        if (jti == null || jti.isBlank() || ttlSeconds <= 0) {
            return;
        }
        String key = KEY_PREFIX + jti;
        redisTemplate.opsForValue().set(key, VALUE, Duration.ofSeconds(ttlSeconds));
        log.info("Blacklisted JWT jti={} for {}s", jti, ttlSeconds);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
    }
}