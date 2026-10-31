package com.agriva.scheduler;

import com.agriva.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskMaintenanceScheduler {

    private final TaskService taskService;
    private final JdbcTemplate jdbc;

    @Scheduled(cron = "0 */5 * * * *")
    public void tick() {
        try {
            taskService.processExpiredTasks();
            taskService.processUnvalidatedTasks();
        } catch (Exception e) {
            log.error("TaskMaintenanceScheduler tick failed: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 5 0 * * *")
    public void dailyProductivity() {
        try { taskService.updateDailyProductivity(); }
        catch (Exception e) { log.error("updateDailyProductivity failed: {}", e.getMessage(), e); }
    }

    @Scheduled(cron = "0 10 0 1 * *")
    public void monthlyReset() {
        try { taskService.resetProductivityMetrics(); }
        catch (Exception e) { log.error("resetProductivityMetrics failed: {}", e.getMessage(), e); }
    }

    /** Prune idempotency markers older than 30 days (Postgres). */
    @Scheduled(cron = "0 30 3 * * *")   // daily 03:30
    @Transactional
    public void cleanupProcessedEvents() {
        try {
            int deleted = jdbc.update(
                    "DELETE FROM consumer_processed_events WHERE processed_at < NOW() - INTERVAL '30 days'");
            if (deleted > 0) log.info("Pruned {} old idempotency markers", deleted);
        } catch (Exception e) {
            log.error("Idempotency-marker cleanup failed: {}", e.getMessage(), e);
        }
    }
}