package com.agriva.shared.security.blacklist;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
public class CaffeineIpBlacklistService implements IpBlacklistService {

    private final int maxFailures;
    private final Cache<String, Integer> failures;
    private final Cache<String, Long>    blocked;
    private final long blockSeconds;

    public CaffeineIpBlacklistService(
            @Value("${agriva.security.ip-blacklist.max-failures:5}") int maxFailures,
            @Value("${agriva.security.ip-blacklist.window-seconds:900}") long windowSeconds,
            @Value("${agriva.security.ip-blacklist.block-seconds:3600}") long blockSeconds) {
        this.maxFailures  = maxFailures;
        this.blockSeconds = blockSeconds;
        this.failures = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(windowSeconds))
                .maximumSize(200_000)
                .build();
        this.blocked = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(blockSeconds))
                .maximumSize(200_000)
                .build();
    }

    @Override public boolean isBlocked(String ip) { return blocked.getIfPresent(ip) != null; }

    @Override public long blockRemainingSeconds(String ip) {
        Long until = blocked.getIfPresent(ip);
        return until == null ? 0 : Math.max(0, (until - System.currentTimeMillis()) / 1000L);
    }

    @Override public void recordFailure(String ip) {
        Integer prev = failures.getIfPresent(ip);
        int next = (prev == null ? 1 : prev + 1);
        failures.put(ip, next);
        if (next >= maxFailures) {
            blocked.put(ip, System.currentTimeMillis() + blockSeconds * 1000L);
            failures.invalidate(ip);
            log.warn("IP {} blocked for {}s after {} failed logins", ip, blockSeconds, next);
        }
    }

    @Override public void clearFailures(String ip) { failures.invalidate(ip); }
}