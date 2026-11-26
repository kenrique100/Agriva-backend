package com.agriva.task.scheduler;

import com.agriva.task.model.entity.Task;
import com.agriva.task.model.enums.TaskStatus;
import com.agriva.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

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
        int processed = 0;

        for (Task t : running) {
            LocalDateTime segmentStart = t.getLastResumeTime() != null
                    ? t.getLastResumeTime()
                    : t.getStartTime();
            if (segmentStart == null) {
                log.warn("Task {} is IN_PROGRESS but has no start/resume timestamp — skipping",
                        t.getId());
                continue;
            }

            long expectedMinutes = (t.getExpectedHours() != null ? t.getExpectedHours() : 0) * 60L;
            long accumulated = t.getTotalWorkedMinutes() != null ? t.getTotalWorkedMinutes() : 0L;
            long remaining = expectedMinutes - accumulated;

            LocalDateTime stopAt;
            long segmentMinutes;

            if (remaining > 0) {

                LocalDateTime budgetDeadline = segmentStart.plusMinutes(remaining);
                stopAt = budgetDeadline.isAfter(now) ? now : budgetDeadline;
                segmentMinutes = Math.max(0,
                        Duration.between(segmentStart, stopAt).toMinutes());
            } else {
                stopAt = now;
                segmentMinutes = Math.max(0,
                        Duration.between(segmentStart, now).toMinutes());
            }

            long newTotal = accumulated + segmentMinutes;

            t.setTotalWorkedMinutes(newTotal);
            t.setActualHours(newTotal / 60.0);
            t.setStopTime(stopAt);
            t.setStatus(TaskStatus.SUBMITTED);
            t.setIsSubmitted(true);
            processed++;
        }
        taskRepository.saveAll(running);
        log.info("Auto-stopped {} overdue task(s) out of {} candidates",
                processed, running.size());
    }
}