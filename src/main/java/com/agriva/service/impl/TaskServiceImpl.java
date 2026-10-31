package com.agriva.service.impl;

import com.agriva.exception.*;
import com.agriva.kafka.producer.TaskProducer;
import com.agriva.mapper.TaskMapper;
import com.agriva.model.dto.task.*;
import com.agriva.model.entity.*;
import com.agriva.model.entity.enums.PersonnelStatus;
import com.agriva.model.entity.enums.StagedTaskStatus;
import com.agriva.model.entity.enums.TaskStatus;
import com.agriva.repository.*;
import com.agriva.service.TaskService;
import com.agriva.utils.TaskExcelParser;
import com.agriva.utils.TaskValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final PersonnelRepository personnelRepository;
    private final StagedTaskRepository stagedRepo;
    private final TaskValidator validator;
    private final TaskMapper mapper;
    private final TaskProducer taskProducer;
    private final TaskExcelParser excelParser;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public TaskResponse create(TaskCreateRequest req, UUID farmId, UUID assignedByUserId) {
        Personnel p = personnelRepository.findByIdAndFarmIdLive(req.getPersonnelId(), farmId)
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

    @Override
    @Transactional(readOnly = true)
    public Page<TaskResponse> list(UUID farmId, UUID personnelId, Pageable pageable) {
        Page<Task> page = personnelId != null
                ? taskRepository.findByFarmIdAndPersonnelIdLive(farmId, personnelId, pageable)
                : taskRepository.findByFarmIdLive(farmId, pageable);
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
            Personnel p = personnelRepository.findByIdAndFarmIdLive(req.getPersonnelId(), farmId)
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

        if (!isMaster) {
            Personnel own = t.getPersonnel();
            if (own.getUserId() == null || !actorUserId.equals(own.getUserId())) {
                throw new PersonnelAccessDeniedException("You can only act on your own tasks");
            }
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
            case "PAUSE" -> {
                accumulateSegment(t, now);
                t.setStopTime(now);
                t.setStatus(TaskStatus.PAUSED);
            }
            case "RESUME", "CONTINUE" -> {
                t.setLastResumeTime(now);
                t.setStopTime(null);
                t.setStatus(TaskStatus.IN_PROGRESS);
            }
            case "STOP" -> {
                accumulateSegment(t, now);
                t.setStopTime(now);
                t.setStatus(TaskStatus.STOPPED);
            }
            case "SUBMIT" -> {
                if (t.getStatus() == TaskStatus.IN_PROGRESS || t.getStatus() == TaskStatus.PAUSED) {
                    accumulateSegment(t, now);
                    t.setStopTime(now);
                }
                t.setIsSubmitted(true);
                t.setStatus(TaskStatus.SUBMITTED);
            }
            case "CANCEL" -> {
                t.setStatus(TaskStatus.CANCELLED);
                t.setStopTime(now);
            }
        }
        log.info("Task {} action={} by user={} isMaster={}", id, a, actorUserId, isMaster);
        return mapper.toResponse(taskRepository.save(t));
    }

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
        Task t = load(id, farmId);
        t.setDeletedAt(LocalDateTime.now());
        taskRepository.save(t);
        log.info("Soft-deleted task {}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductivityStatsResponse productivity(UUID personnelId, UUID farmId) {
        Personnel p = personnelRepository.findByIdAndFarmIdLive(personnelId, farmId)
                .orElseThrow(() -> new PersonnelNotFoundException(
                        "Personnel not found: " + personnelId));

        List<Task> tasks = taskRepository.findByPersonnelIdAndIsValidatedLive(personnelId, true);
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
                TaskStatus.SUBMITTED, startOfDay, endOfDay);
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
        personnelRepository.findByStatusLive(PersonnelStatus.ACTIVE).forEach(p -> {
            if (yesterday.equals(p.getLastProductivityUpdate())) return;

            // Use the Live finder (soft-deleted tasks excluded).
            List<Task> completed = taskRepository.findByPersonnelIdAndIsValidatedLive(
                            p.getId(), true)
                    .stream()
                    .filter(t -> t.getValidationTime() != null
                            && t.getValidationTime().toLocalDate().equals(yesterday))
                    .toList();

            if (!completed.isEmpty()) {
                double h = completed.stream()
                        .mapToDouble(t -> t.getActualHours() != null ? t.getActualHours() : 0.0)
                        .sum();
                p.setTotalHoursWorkedLast30Days(
                        (p.getTotalHoursWorkedLast30Days() == null
                                ? 0.0 : p.getTotalHoursWorkedLast30Days()) + h);
                p.setWorkingDaysCount(p.getWorkingDaysCount() + 1);
                p.setLastProductivityUpdate(yesterday);
                personnelRepository.save(p);
            }
        });
    }

    @Override
    @Transactional
    public void resetProductivityMetrics() {
        int n = personnelRepository.bulkResetProductivity(LocalDate.now());
        log.info("Reset 30-day productivity metrics for {} active personnel", n);
    }

    @Override
    public TaskBulkUploadResponse bulkUpload(MultipartFile file, UUID farmId,
                                             UUID userId, String email) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required");
        }
        List<TaskExcelParser.ParsedRow> rows;
        try { rows = excelParser.parse(file.getInputStream(), farmId); }
        catch (Exception e) {
            throw new IllegalArgumentException("Failed to read Excel file: " + e.getMessage(), e);
        }

        TaskBulkUploadResponse response = new TaskBulkUploadResponse();
        response.setTotalRows(rows.size());

        for (TaskExcelParser.ParsedRow row : rows) {
            if (row.isError()) {
                response.setFailed(response.getFailed() + 1);
                response.getResults().add(new TaskBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", row.error()));
                continue;
            }
            UUID submissionId = UUID.randomUUID();
            try {
                String payload = objectMapper.writeValueAsString(row.request());
                taskProducer.publish(new TaskSubmissionEvent(
                        submissionId, farmId, userId, email, payload, LocalDateTime.now()));
                response.setQueued(response.getQueued() + 1);
                response.getResults().add(new TaskBulkUploadResponse.RowResult(
                        row.rowNumber(), submissionId, "QUEUED", null));
            } catch (Exception e) {
                response.setFailed(response.getFailed() + 1);
                response.getResults().add(new TaskBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", e.getMessage()));
            }
        }

        log.info("Bulk task upload for farm {}: total={}, queued={}, failed={}",
                farmId, response.getTotalRows(), response.getQueued(), response.getFailed());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StagedTaskResponse> listStaging(UUID farmId, String status, Pageable pageable) {
        Page<StagedTask> page;
        if (status != null && !status.isBlank()) {
            page = stagedRepo.findByFarmIdAndStatus(
                    farmId, StagedTaskStatus.valueOf(status.toUpperCase()), pageable);
        } else {
            page = stagedRepo.findByFarmId(farmId, pageable);
        }
        return page.map(s -> new StagedTaskResponse(
                s.getId(), s.getFarmId(), s.getStatus().name(),
                s.getValidationErrors(), s.getPayloadJson(),
                s.getSubmittedByUserId(), s.getSubmittedByEmail(),
                s.getSubmittedAt(), s.getLastAttemptAt(), s.getNextRetryAt(),
                s.getProcessedAt(), s.getMovedTaskId(), s.getRetryCount()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> stagingStats(UUID farmId) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (StagedTaskStatus s : StagedTaskStatus.values()) {
            out.put(s.name(), stagedRepo.countByFarmIdAndStatus(farmId, s));
        }
        return out;
    }

    private Task load(UUID id, UUID farmId) {
        return taskRepository.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with ID: " + id));
    }
}