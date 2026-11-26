package com.agriva.shared.scheduler;

import com.agriva.shared.lock.DistributedLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RetentionCleanupScheduler {

    private static final int KEEP_STAGING_DAYS     = 30;
    private static final int KEEP_IDEMPOTENCY_DAYS = 30;
    private static final int CREATE_MONTHS_AHEAD   = 3;

    private static final List<String> STAGING_TABLES = List.of(
            "staged_income",
            "staged_expense",
            "staged_task",
            "staged_salary",
            "staged_crop_cycle",
            "staged_feeding_ingredient",
            "staged_feeding_formulation",
            "staged_feeding_production",
            "staged_feeding_event"
    );

    private final JdbcTemplate jdbc;
    private final DistributedLockService lock;

    @Scheduled(cron = "0 30 3 * * *")
    public void daily() {
        lock.runIfLocked("retention-cleanup", Duration.ofMinutes(30), () -> {
            List<RuntimeException> failures = new ArrayList<>();
            rollPartitionsForward(failures);
            dropOldPartitions(failures);
            pruneIdempotency(failures);

            if (!failures.isEmpty()) {
                throw new IllegalStateException(
                        "Retention cleanup finished with " + failures.size()
                                + " failure(s); first: " + failures.get(0).getMessage(),
                        failures.get(0));
            }
            return null;
        });
    }

    private void rollPartitionsForward(List<RuntimeException> failures) {
        for (String table : STAGING_TABLES) {
            try {
                jdbc.queryForObject("SELECT ensure_partitions(?, ?, ?)", Object.class,
                        table, CREATE_MONTHS_AHEAD, 0);
            } catch (RuntimeException e) {
                log.error("ensure_partitions failed for {}: {}", table, e.getMessage(), e);
                failures.add(e);
            }
        }
        log.info("Partition roll-forward complete for {} tables", STAGING_TABLES.size());
    }

    private void dropOldPartitions(List<RuntimeException> failures) {
        for (String table : STAGING_TABLES) {
            try {
                Integer dropped = jdbc.queryForObject(
                        "SELECT drop_partitions_older_than(?, ?)", Integer.class,
                        table, KEEP_STAGING_DAYS);
                if (dropped != null && dropped > 0) {
                    log.info("Dropped {} old partition(s) from {}", dropped, table);
                }
            } catch (RuntimeException e) {
                log.error("drop_partitions_older_than failed for {}: {}",
                        table, e.getMessage(), e);
                failures.add(e);
            }
        }
    }

    private void pruneIdempotency(List<RuntimeException> failures) {
        try {
            int deleted = jdbc.update(
                    "DELETE FROM consumer_processed_events "
                            + "WHERE processed_at < NOW() - (? || ' days')::INTERVAL",
                    KEEP_IDEMPOTENCY_DAYS);
            if (deleted > 0) {
                log.info("Pruned {} stale idempotency markers older than {} day(s)",
                        deleted, KEEP_IDEMPOTENCY_DAYS);
            }
        } catch (RuntimeException e) {
            log.error("Idempotency prune failed: {}", e.getMessage(), e);
            failures.add(e);
        }
    }
}