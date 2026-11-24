package com.agriva.task.scheduler;

import com.agriva.task.model.entity.Task;
import com.agriva.task.model.enums.TaskStatus;
import com.agriva.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
        for (Task t : running) {
            long accumulated = t.getTotalWorkedMinutes() != null ? t.getTotalWorkedMinutes() : 0L;
            long expectedMinutes = (t.getExpectedHours() != null ? t.getExpectedHours() : 0) * 60L;

            long remaining = expectedMinutes - accumulated;
            LocalDateTime stopAt;
            if (remaining <= 0) {
                stopAt = now;
            } else {
                stopAt = t.getLastResumeTime().plusMinutes(remaining);
                if (stopAt.isAfter(now)) stopAt = now;
            }

            long newTotal = Math.max(accumulated, expectedMinutes);
            t.setTotalWorkedMinutes(newTotal);
            t.setActualHours(newTotal / 60.0);
            t.setStopTime(stopAt);
            t.setStatus(TaskStatus.SUBMITTED);
            t.setIsSubmitted(true);
        }
        taskRepository.saveAll(running);
        log.info("Auto-stopped {} overdue task(s)", running.size());
    }
}