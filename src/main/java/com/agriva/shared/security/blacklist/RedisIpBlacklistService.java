package com.agriva.shared.security.blacklist;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisIpBlacklistService implements IpBlacklistService {

    private static final String FAIL_PREFIX  = "agriva:ip:fail:";
    private static final String BLOCK_PREFIX = "agriva:ip:block:";

    private final StringRedisTemplate redis;

    @Value("${agriva.security.ip-blacklist.max-failures:5}")
    private int maxFailures;

    @Value("${agriva.security.ip-blacklist.window-seconds:900}")
    private long windowSeconds;

    @Value("${agriva.security.ip-blacklist.block-seconds:3600}")
    private long blockSeconds;

    @Override
    public boolean isBlocked(String ip) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(BLOCK_PREFIX + ip));
        } catch (Exception e) {
            log.warn("Redis block lookup failed for {}: {}", ip, e.getMessage());
            return false; // fail-open during Redis outage
        }
    }

    @Override
    public long blockRemainingSeconds(String ip) {
        try {
            Long ttl = redis.getExpire(BLOCK_PREFIX + ip);
            return ttl != null && ttl > 0 ? ttl : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void recordFailure(String ip) {
        String failKey = FAIL_PREFIX + ip;
        try {
            Long n = redis.opsForValue().increment(failKey);
            if (n != null && n == 1L) {
                redis.expire(failKey, Duration.ofSeconds(windowSeconds));
            }
            if (n != null && n >= maxFailures) {
                redis.opsForValue().set(BLOCK_PREFIX + ip, "1", Duration.ofSeconds(blockSeconds));
                redis.delete(failKey);
                log.warn("IP {} blocked for {}s after {} failed logins", ip, blockSeconds, n);
            }
        } catch (Exception e) {
            log.warn("Redis failure-record failed for {}: {}", ip, e.getMessage());
        }
    }

    @Override
    public void clearFailures(String ip) {
        try {
            redis.delete(FAIL_PREFIX + ip);
        } catch (Exception ignored) { }
    }
}