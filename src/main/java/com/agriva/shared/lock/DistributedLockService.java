package com.agriva.shared.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Lua script ensures that we only delete the lock if the value matches
     * our unique token. This prevents deleting a lock that was acquired by
     * another instance after our lock expired.
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    /**
     * Attempts to acquire a distributed lock. If successful, it executes
     * the provided task and releases the lock. If not, it returns null.
     *
     * @param lockKey  The unique name of the lock (e.g., "retention-cleanup").
     * @param timeout  The maximum time to hold the lock before it auto-expires.
     * @param task     The task to run while the lock is held.
     * @return The result of the task, or null if the lock couldn't be acquired.
     */
    public <T> T runIfLocked(String lockKey, Duration timeout, Supplier<T> task) {
        String lockValue = UUID.randomUUID().toString();

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, timeout);

        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("Could not acquire distributed lock for key: {}", lockKey);
            return null;
        }

        try {
            log.debug("Acquired distributed lock for key: {}", lockKey);
            return task.get();
        } finally {
            try {
                redisTemplate.execute(
                        UNLOCK_SCRIPT,
                        Collections.singletonList(lockKey),
                        lockValue
                );
                log.debug("Released distributed lock for key: {}", lockKey);
            } catch (Exception e) {
                log.warn("Failed to release distributed lock for key: {}", lockKey, e);
            }
        }
    }
}