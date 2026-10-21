package com.agriva.scheduler;

import com.agriva.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Named TaskMaintenanceScheduler (not TaskScheduler) to avoid colliding with
 * Spring Boot's autoconfigured "taskScheduler" bean produced by
 * TaskSchedulingAutoConfiguration. Renaming is the correct fix — enabling
 * bean overriding would shadow Boot's scheduler and break @Scheduled dispatch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskMaintenanceScheduler {

    private final TaskService taskService;

    @Scheduled(cron = "0 */5 * * * *") // every 5 minutes
    public void tick() {
        try {
            taskService.processExpiredTasks();
            taskService.processUnvalidatedTasks();
        } catch (Exception e) {
            log.error("TaskMaintenanceScheduler tick failed: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 5 0 * * *") // daily 00:05
    public void dailyProductivity() {
        try {
            taskService.updateDailyProductivity();
        } catch (Exception e) {
            log.error("updateDailyProductivity failed: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 10 0 1 * *") // 1st of month, 00:10
    public void monthlyReset() {
        try {
            taskService.resetProductivityMetrics();
        } catch (Exception e) {
            log.error("resetProductivityMetrics failed: {}", e.getMessage(), e);
        }
    }
}