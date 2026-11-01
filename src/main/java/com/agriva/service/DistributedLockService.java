package com.agriva.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Best-effort distributed lock backed by Redis.
 * <p>
 * Used by @Scheduled jobs so that, when the app runs on more than one
 * instance, only one instance runs each batch. On single-instance
 * deployments the lock is always acquired and does nothing harmful.
 * <p>
 * This is a NON-reentrant, self-expiring lock. It is enough for
 * scheduling coalescence; it is NOT a substitute for the pessimistic
 * row-level locks that already protect the staging validators.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {

    private static final String KEY_PREFIX = "agriva:sched-lock:";
    private final StringRedisTemplate redis;

    public boolean tryLock(String name, Duration ttl) {
        try {
            Boolean acquired = redis.opsForValue()
                    .setIfAbsent(KEY_PREFIX + name, UUID.randomUUID().toString(), ttl);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("Redis unavailable while acquiring lock {}: {}", name, e.getMessage());
            return true;
        }
    }

    public void unlock(String name) {
        try { redis.delete(KEY_PREFIX + name); }
        catch (Exception e) { log.warn("Failed to release lock {}: {}", name, e.getMessage()); }
    }

    public <T> Optional<T> runIfLocked(String name, Duration ttl, Supplier<T> work) {
        if (!tryLock(name, ttl)) {
            log.debug("Lock {} held elsewhere, skipping this tick", name);
            return Optional.empty();
        }
        try { return Optional.ofNullable(work.get()); }
        finally { unlock(name); }
    }
}