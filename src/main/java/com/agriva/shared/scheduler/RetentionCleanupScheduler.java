package com.agriva.shared.scheduler;

import com.agriva.dashboard.service.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetentionCleanupScheduler {

    private static final int KEEP_STAGING_DAYS   = 30;
    private static final int CREATE_MONTHS_AHEAD = 3;

    private static final List<String> STAGING_TABLES = List.of(
            "staged_income", "staged_expense", "staged_task",
            "staged_salary", "staged_crop_cycle",
            "consumer_processed_events"
    );

    private final JdbcTemplate jdbc;
    private final DistributedLockService lock;

    @Scheduled(cron = "0 30 3 * * *")
    public void daily() {
        lock.runIfLocked("retention-cleanup", Duration.ofMinutes(30), () -> {
            rollPartitionsForward();
            dropOldPartitions();
            pruneIdempotency();
            return null;
        });
    }

    private void rollPartitionsForward() {
        for (String table : STAGING_TABLES) {
            try {
                jdbc.queryForObject(
                        "SELECT ensure_partitions(?, ?, ?)",
                        Object.class,
                        table, CREATE_MONTHS_AHEAD, 0);
            } catch (Exception e) {
                log.error("ensure_partitions failed for {}: {}", table, e.getMessage());
            }
        }
        log.info("Partition roll-forward complete for {} tables", STAGING_TABLES.size());
    }

    private void dropOldPartitions() {
        for (String table : STAGING_TABLES) {
            try {
                Integer dropped = jdbc.queryForObject(
                        "SELECT drop_partitions_older_than(?, ?)",
                        Integer.class, table, KEEP_STAGING_DAYS);
                if (dropped != null && dropped > 0) {
                    log.info("Dropped {} old partition(s) from {}", dropped, table);
                }
            } catch (Exception e) {
                log.error("drop_partitions_older_than failed for {}: {}", table, e.getMessage());
            }
        }
    }

    private void pruneIdempotency() {
        try {
            int deleted = jdbc.update(
                    "DELETE FROM consumer_processed_events " +
                            "WHERE processed_at < NOW() - INTERVAL '30 days'");
            if (deleted > 0) log.info("Pruned {} stale idempotency markers", deleted);
        } catch (Exception e) {
            log.error("Idempotency prune failed: {}", e.getMessage());
        }
    }
}