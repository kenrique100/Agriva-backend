package com.agriva.service.impl;

import com.agriva.exception.*;
import com.agriva.mapper.TaskMapper;
import com.agriva.model.dto.task.*;
import com.agriva.model.entity.*;
import com.agriva.repository.*;
import com.agriva.service.TaskService;
import com.agriva.utils.TaskValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final PersonnelRepository personnelRepository;
    private final TaskValidator validator;
    private final TaskMapper mapper;

    @Override
    @Transactional
    public TaskResponse create(TaskCreateRequest req, UUID farmId, UUID assignedByUserId) {
        Personnel p = personnelRepository.findByIdAndFarmId(req.getPersonnelId(), farmId)
                .orElseThrow(() -> new PersonnelNotFoundException(
                        "Personnel not found with ID: " + req.getPersonnelId()));

        Task t = Task.builder()
                .farmId(farmId)
                .personnel(p)
                .title(req.getTitle())
                .description(req.getDescription())
                .deadline(req.getDeadline())
                .expectedHours(req.getExpectedHours())
                .status(TaskStatus.PENDING)
                .assignedByUserId(assignedByUserId)
                .build();
        return mapper.toResponse(taskRepository.save(t));
    }

    /**
     * FIX: wrap in readOnly=true so the Hibernate Session stays open
     * while TaskMapper.toResponse() touches the lazily-loaded
     * Task.personnel proxy. Without this, spring.jpa.open-in-view=false
     * closes the session before mapping → LazyInitializationException.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<TaskResponse> list(UUID farmId, UUID personnelId, Pageable pageable) {
        Page<Task> page = personnelId != null
                ? taskRepository.findByFarmIdAndPersonnelId(farmId, personnelId, pageable)
                : taskRepository.findByFarmId(farmId, pageable);
        return page.map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse get(UUID id, UUID farmId) {
        return mapper.toResponse(load(id, farmId));
    }

    @Override
    @Transactional
    public TaskResponse update(UUID id, TaskUpdateRequest req, UUID farmId) {
        Task t = load(id, farmId);
        if (req.getTitle() != null) t.setTitle(req.getTitle());
        if (req.getDescription() != null) t.setDescription(req.getDescription());
        if (req.getDeadline() != null) t.setDeadline(req.getDeadline());
        if (req.getExpectedHours() != null) t.setExpectedHours(req.getExpectedHours());
        if (req.getPersonnelId() != null && !req.getPersonnelId().equals(t.getPersonnel().getId())) {
            Personnel p = personnelRepository.findByIdAndFarmId(req.getPersonnelId(), farmId)
                    .orElseThrow(() -> new PersonnelNotFoundException(
                            "Personnel not found: " + req.getPersonnelId()));
            t.setPersonnel(p);
        }
        return mapper.toResponse(taskRepository.save(t));
    }

    @Override
    @Transactional
    public TaskResponse action(UUID id, UUID farmId, UUID actorUserId, boolean isMaster, String action) {
        Task t = load(id, farmId);

        if (!isMaster && t.getPersonnel().getUserId() != null
                && !actorUserId.equals(t.getPersonnel().getUserId())) {
            throw new PersonnelAccessDeniedException(
                    "You can only act on your own tasks");
        }

        validator.validateAction(t, action);
        String a = action.toUpperCase();
        LocalDateTime now = LocalDateTime.now();

        switch (a) {
            case "START" -> {
                t.setStatus(TaskStatus.IN_PROGRESS);
                t.setStartTime(now);
                t.setLastResumeTime(now);
            }
            case "STOP" -> {
                accumulateSegment(t, now);
                t.setStopTime(now);
                t.setStatus(TaskStatus.STOPPED);
            }
            case "CONTINUE" -> {
                t.setLastResumeTime(now);
                t.setStopTime(null);
                t.setStatus(TaskStatus.IN_PROGRESS);
            }
            case "SUBMIT" -> {
                accumulateSegment(t, now);
                t.setStopTime(now);
                t.setIsSubmitted(true);
            }
            case "CANCEL" -> {
                t.setStatus(TaskStatus.CANCELLED);
                t.setStopTime(now);
            }
        }
        log.info("Task {} action={} by user={}", id, a, actorUserId);
        return mapper.toResponse(taskRepository.save(t));
    }

    /** Fixes the KBF double-count bug — only the last segment is added. */
    private void accumulateSegment(Task t, LocalDateTime now) {
        LocalDateTime segmentStart = t.getLastResumeTime() != null
                ? t.getLastResumeTime()
                : t.getStartTime();
        if (segmentStart == null) return;
        long minutes = Math.max(0, Duration.between(segmentStart, now).toMinutes());
        long total = (t.getTotalWorkedMinutes() == null ? 0L : t.getTotalWorkedMinutes()) + minutes;
        t.setTotalWorkedMinutes(total);
        t.setActualHours(total / 60.0);
    }

    @Override
    @Transactional
    public TaskResponse validate(UUID id, UUID farmId, UUID adminUserId, boolean approve) {
        Task t = load(id, farmId);
        validator.validateCanValidate(t);

        t.setValidationTime(LocalDateTime.now());
        t.setValidatedByUserId(adminUserId);
        t.setIsSubmitted(false);

        if (approve) {
            t.setIsValidated(true);
            t.setStatus(TaskStatus.COMPLETED);
            rollProductivity(t.getPersonnel(), t.getActualHours());
        } else {
            t.setIsValidated(false);
            t.setStatus(TaskStatus.CANCELLED);
        }
        log.info("Task {} validated={}", id, approve);
        return mapper.toResponse(taskRepository.save(t));
    }

    private void rollProductivity(Personnel p, Double hours) {
        if (hours == null || hours <= 0) return;
        LocalDate today = LocalDate.now();
        if (!today.equals(p.getLastProductivityUpdate())) {
            p.setWorkingDaysCount(p.getWorkingDaysCount() + 1);
            p.setTotalHoursWorkedLast30Days(
                    (p.getTotalHoursWorkedLast30Days() == null ? 0.0 : p.getTotalHoursWorkedLast30Days())
                            + hours);
            p.setLastProductivityUpdate(today);
            personnelRepository.save(p);
        }
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID farmId) {
        taskRepository.delete(load(id, farmId));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductivityStatsResponse productivity(UUID personnelId, UUID farmId) {
        Personnel p = personnelRepository.findByIdAndFarmId(personnelId, farmId)
                .orElseThrow(() -> new PersonnelNotFoundException("Personnel not found: " + personnelId));

        List<Task> tasks = taskRepository.findByPersonnelIdAndIsValidated(personnelId, true);
        double hours = tasks.stream()
                .mapToDouble(t -> t.getActualHours() != null ? t.getActualHours() : 0.0)
                .sum();
        int days = p.getWorkingDaysCount() == null ? 0 : p.getWorkingDaysCount();
        double dailyAvg = days > 0 ? hours / days : 0.0;

        double expectedHours = tasks.stream()
                .mapToDouble(t -> t.getExpectedHours() != null ? t.getExpectedHours() : 0)
                .sum();
        double pct = expectedHours > 0 ? (hours / expectedHours) * 100.0 : 0.0;

        return ProductivityStatsResponse.builder()
                .totalHoursWorked(hours)
                .dailyAverage(dailyAvg)
                .workingDays(days)
                .periodStartDate(p.getCurrentPeriodStartDate())
                .periodEndDate(p.getCurrentPeriodStartDate().plusDays(29))
                .productivityPercentage(pct)
                .build();
    }

    @Override
    @Transactional
    public void processExpiredTasks() {
        List<Task> expired = taskRepository.findByDeadlineBeforeAndStatus(
                LocalDate.now().minusDays(1), TaskStatus.PENDING);
        expired.forEach(t -> t.setStatus(TaskStatus.UNCOMPLETED));
        if (!expired.isEmpty()) {
            taskRepository.saveAll(expired);
            log.info("Marked {} expired tasks UNCOMPLETED", expired.size());
        }
    }

    @Override
    @Transactional
    public void processUnvalidatedTasks() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59);
        List<Task> unvalidated = taskRepository.findSubmittedTasksWithStopTimeBetween(
                TaskStatus.IN_PROGRESS, startOfDay, endOfDay);
        unvalidated.forEach(t -> {
            t.setStatus(TaskStatus.UNCOMPLETED);
            t.setIsValidated(false);
            t.setIsSubmitted(false);
        });
        if (!unvalidated.isEmpty()) {
            taskRepository.saveAll(unvalidated);
            log.info("Marked {} unvalidated tasks UNCOMPLETED", unvalidated.size());
        }
    }

    @Override
    @Transactional
    public void updateDailyProductivity() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        personnelRepository.findByStatus(PersonnelStatus.ACTIVE).forEach(p -> {
            if (yesterday.equals(p.getLastProductivityUpdate())) return;
            List<Task> completed = taskRepository.findByPersonnelIdAndIsValidated(p.getId(), true)
                    .stream()
                    .filter(t -> t.getValidationTime() != null
                            && t.getValidationTime().toLocalDate().equals(yesterday))
                    .toList();
            if (!completed.isEmpty()) {
                double h = completed.stream()
                        .mapToDouble(t -> t.getActualHours() != null ? t.getActualHours() : 0.0)
                        .sum();
                p.setTotalHoursWorkedLast30Days(
                        (p.getTotalHoursWorkedLast30Days() == null ? 0.0 : p.getTotalHoursWorkedLast30Days()) + h);
                p.setWorkingDaysCount(p.getWorkingDaysCount() + 1);
                p.setLastProductivityUpdate(yesterday);
                personnelRepository.save(p);
            }
        });
    }

    @Override
    @Transactional
    public void resetProductivityMetrics() {
        LocalDate today = LocalDate.now();
        personnelRepository.findByStatus(PersonnelStatus.ACTIVE).forEach(p -> {
            p.setTotalHoursWorkedLast30Days(0.0);
            p.setWorkingDaysCount(0);
            p.setLastProductivityResetDate(today);
            p.setCurrentPeriodStartDate(today);
            personnelRepository.save(p);
        });
        log.info("Reset 30-day productivity metrics for all active personnel");
    }

    private Task load(UUID id, UUID farmId) {
        return taskRepository.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with ID: " + id));
    }
}