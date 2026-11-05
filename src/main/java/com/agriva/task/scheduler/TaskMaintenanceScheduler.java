package com.agriva.task.scheduler;

import com.agriva.dashboard.service.DistributedLockService;
import com.agriva.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * The idempotency-marker cleanup that used to live here has moved into
 * RetentionCleanupScheduler.daily() so all housekeeping runs under one
 * distributed lock.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskMaintenanceScheduler {

    private final TaskService taskService;
    private final DistributedLockService lock;

    @Scheduled(cron = "0 */5 * * * *") // every 5 minutes
    public void tick() {
        lock.runIfLocked("task-maintenance-tick", Duration.ofMinutes(4), () -> {
            try {
                taskService.processExpiredTasks();
                taskService.processUnvalidatedTasks();
            } catch (Exception e) {
                log.error("TaskMaintenanceScheduler tick failed: {}", e.getMessage(), e);
            }
            return null;
        });
    }

    @Scheduled(cron = "0 5 0 * * *") // daily 00:05
    public void dailyProductivity() {
        lock.runIfLocked("task-daily-productivity", Duration.ofMinutes(30), () -> {
            try { taskService.updateDailyProductivity(); }
            catch (Exception e) { log.error("updateDailyProductivity failed: {}", e.getMessage(), e); }
            return null;
        });
    }

    @Scheduled(cron = "0 10 0 1 * *") // 1st of month, 00:10
    public void monthlyReset() {
        lock.runIfLocked("task-monthly-reset", Duration.ofMinutes(30), () -> {
            try { taskService.resetProductivityMetrics(); }
            catch (Exception e) { log.error("resetProductivityMetrics failed: {}", e.getMessage(), e); }
            return null;
        });
    }
}