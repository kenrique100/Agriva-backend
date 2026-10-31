package com.agriva.scheduler;

import com.agriva.model.entity.Task;
import com.agriva.model.entity.enums.TaskStatus;
import com.agriva.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Runs every minute. For any IN_PROGRESS task whose accumulated time
 * (totalWorkedMinutes + elapsed since last résumé) has reached or exceeded
 * expectedHours, the timer is force-stopped at exactly expectedHours and the
 * task is auto-submitted so MASTER can validate it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskAutoStopScheduler {

    private final TaskRepository taskRepository;

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void autoStopOverdueTasks() {
        List<Task> running = taskRepository.findTasksDueForAutoStop();
        if (running.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (Task t : running) {
            long accumulated = t.getTotalWorkedMinutes() != null ? t.getTotalWorkedMinutes() : 0L;
            long expectedMinutes = t.getExpectedHours() * 60L;
            LocalDateTime stopAt = t.getLastResumeTime().plusMinutes(expectedMinutes - accumulated);
            if (stopAt.isAfter(now)) stopAt = now;
            t.setTotalWorkedMinutes(expectedMinutes);
            t.setActualHours(expectedMinutes / 60.0);
            t.setStopTime(stopAt);
            t.setStatus(TaskStatus.SUBMITTED);
            t.setIsSubmitted(true);
        }
        taskRepository.saveAll(running);
        log.info("Auto-stopped {} overdue task(s)", running.size());
    }
}