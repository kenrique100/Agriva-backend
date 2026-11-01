package com.agriva.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Caffeine-backed in-process cache.
 *
 * Different TTL per cache:
 *   - reference data (farm / user / crops / fields): 5–15 min
 *   - volatile aggregates (dashboardTotals): 30 s
 *   - activeInvestment: 30 s + explicit eviction on activate/deactivate
 *
 * Cache size is bounded at 100 000 entries total. For 1 M users this
 * is more than enough — the working set of "currently active farms" is
 * always a small fraction of the total.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();

        manager.setCaches(List.of(
                build(CacheKeys.FARM_BY_ID,        Duration.ofMinutes(5),  10_000),
                build(CacheKeys.USER_BY_ID,        Duration.ofMinutes(2),  50_000),
                build(CacheKeys.CROPS_BY_FARM,     Duration.ofMinutes(10), 10_000),
                build(CacheKeys.FIELDS_BY_FARM,    Duration.ofMinutes(10), 10_000),
                build(CacheKeys.ACTIVE_INVESTMENT, Duration.ofSeconds(30), 10_000),
                build(CacheKeys.DASHBOARD_TOTALS,  Duration.ofSeconds(30), 10_000)
        ));
        return manager;
    }

    private CaffeineCache build(String name, Duration ttl, long maxEntries) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfterWrite(ttl)
                .recordStats()
                .build());
    }
}