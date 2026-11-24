package com.agriva.task.service.impl;

import com.agriva.personnel.models.entity.Personnel;
import com.agriva.personnel.models.enums.PersonnelStatus;
import com.agriva.personnel.repository.PersonnelRepository;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.PersonnelAccessDeniedException;
import com.agriva.shared.exception.PersonnelNotFoundException;
import com.agriva.shared.exception.TaskNotFoundException;
import com.agriva.task.kafka.producer.TaskProducer;
import com.agriva.task.mapper.TaskMapper;
import com.agriva.task.model.dto.requests.TaskCreateRequest;
import com.agriva.task.model.dto.requests.TaskSubmissionEvent;
import com.agriva.task.model.dto.requests.TaskUpdateRequest;
import com.agriva.task.model.dto.response.ProductivityStatsResponse;
import com.agriva.task.model.dto.response.StagedTaskResponse;
import com.agriva.task.model.dto.response.TaskBulkUploadResponse;
import com.agriva.task.model.dto.response.TaskResponse;
import com.agriva.task.model.entity.StagedTask;
import com.agriva.task.model.entity.Task;
import com.agriva.task.model.enums.StagedTaskStatus;
import com.agriva.task.model.enums.TaskStatus;
import com.agriva.task.repository.StagedTaskRepository;
import com.agriva.task.repository.TaskRepository;
import com.agriva.task.service.TaskService;
import com.agriva.task.utils.TaskExcelParser;
import com.agriva.task.utils.TaskValidator;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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

        if (p.getStatus() != PersonnelStatus.ACTIVE) {
            throw new BusinessException("Cannot assign tasks to personnel who are not ACTIVE.");
        }

        Task t = Task.builder()
                .farmId(farmId)
                .personnelId(p.getId())
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
    public Page<TaskResponse> list(UUID farmId, UUID personnelId, String status, Pageable pageable) {
        TaskStatus statusEnum = parseStatus(status);

        Page<Task> page;
        if (personnelId != null && statusEnum != null) {
            page = taskRepository.findByFarmIdAndPersonnelIdAndStatusLive(
                    farmId, personnelId, statusEnum, pageable);
        } else if (personnelId != null) {
            page = taskRepository.findByFarmIdAndPersonnelIdLive(farmId, personnelId, pageable);
        } else if (statusEnum != null) {
            page = taskRepository.findByFarmIdAndStatusLive(farmId, statusEnum, pageable);
        } else {
            page = taskRepository.findByFarmIdLive(farmId, pageable);
        }

        List<UUID> personnelIds = page.getContent().stream()
                .map(Task::getPersonnelId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // FIX — Collectors.toMap throws NPE on a null value, so coalesce the name.
        Map<UUID, String> nameById = personnelIds.isEmpty()
                ? Map.of()
                : personnelRepository.findAllById(personnelIds).stream()
                  .filter(p -> farmId.equals(p.getFarmId()))
                  .filter(p -> p.getDeletedAt() == null)
                  .collect(Collectors.toMap(
                          Personnel::getId,
                          p -> p.getName() != null ? p.getName() : "Unknown",
                          (a, b) -> a));

        return page.map(t -> mapper.toResponse(t, nameById));
    }

    private TaskStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return TaskStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid status filter: " + status);
        }
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

        // on the old assignee; a CANCELLED task is an audit record and must stay
        // frozen. Title/description/deadline corrections on live tasks still work.
        if (t.getStatus() == TaskStatus.CANCELLED) {
            throw new BusinessException("Cannot edit a cancelled task");
        }
        if (t.getStatus() == TaskStatus.COMPLETED
                && req.getPersonnelId() != null
                && !req.getPersonnelId().equals(t.getPersonnelId())) {
            throw new BusinessException("Cannot reassign a completed task");
        }

        if (req.getTitle() != null) t.setTitle(req.getTitle());
        if (req.getDescription() != null) t.setDescription(req.getDescription());
        if (req.getDeadline() != null) t.setDeadline(req.getDeadline());
        if (req.getExpectedHours() != null) t.setExpectedHours(req.getExpectedHours());

        if (req.getPersonnelId() != null && !req.getPersonnelId().equals(t.getPersonnelId())) {
            Personnel p = personnelRepository.findByIdAndFarmIdLive(req.getPersonnelId(), farmId)
                    .orElseThrow(() -> new PersonnelNotFoundException(
                            "Personnel not found: " + req.getPersonnelId()));

            if (p.getStatus() != PersonnelStatus.ACTIVE) {
                throw new BusinessException("Cannot assign tasks to personnel who are not ACTIVE.");
            }
            t.setPersonnelId(p.getId());
        }
        return mapper.toResponse(taskRepository.save(t));
    }

    @Override
    @Transactional
    public TaskResponse action(UUID id, UUID farmId, UUID actorUserId, boolean isMaster, String action) {
        // inside validator.validateAction.
        if (action == null || action.isBlank()) {
            throw new BusinessException("Action is required");
        }

        Task t = load(id, farmId);

        if (!isMaster) {
            Personnel own = personnelRepository.findById(t.getPersonnelId())
                    .orElseThrow(() -> new PersonnelAccessDeniedException("Personnel not found"));
            if (own.getUserId() == null || !actorUserId.equals(own.getUserId())) {
                throw new PersonnelAccessDeniedException("You can only act on your own tasks");
            }
            // Cancel now destroys the row — sub-users can PAUSE or STOP instead.
            if ("CANCEL".equalsIgnoreCase(action)) {
                throw new PersonnelAccessDeniedException(
                        "Only the farm master can cancel and remove tasks");
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
                // HARD DELETE. Snapshot the response first so the client still
                // gets a well-formed body. The only FK pointing at `tasks` is
                // staged_task.moved_task_id — we clear it defensively below in
                // addition to relying on DELETE SET NULL.
                t.setStatus(TaskStatus.CANCELLED);
                t.setStopTime(now);
                TaskResponse snapshot = mapper.toResponse(t);

                stagedRepo.clearMovedTaskId(t.getId());   // FIX — defensive FK clear
                taskRepository.delete(t);

                log.info("Task {} action=CANCEL — hard deleted by user={} isMaster={}",
                        id, actorUserId, isMaster);
                return snapshot;
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
            personnelRepository.findById(t.getPersonnelId())
                    .ifPresent(p -> rollProductivity(p, t.getActualHours()));
        } else {
            // Rejection keeps the row — distinct from user-initiated CANCEL.
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
            int days = p.getWorkingDaysCount() == null ? 0 : p.getWorkingDaysCount();
            p.setWorkingDaysCount(days + 1);
            p.setLastProductivityUpdate(today);
        }
        p.setTotalHoursWorkedLast30Days(
                (p.getTotalHoursWorkedLast30Days() == null ? 0.0 : p.getTotalHoursWorkedLast30Days()) + hours);
        personnelRepository.save(p);
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

        LocalDate periodStart = p.getCurrentPeriodStartDate() != null
                ? p.getCurrentPeriodStartDate()
                : LocalDate.now();

        return ProductivityStatsResponse.builder()
                .totalHoursWorked(hours)
                .dailyAverage(dailyAvg)
                .workingDays(days)
                .periodStartDate(periodStart)
                .periodEndDate(LocalDate.now())
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
        LocalDateTime cutoff = LocalDate.now().minusDays(1).atStartOfDay();
        List<Task> unvalidated = taskRepository.findSubmittedTasksWithStopTimeBefore(
                TaskStatus.SUBMITTED, cutoff);
        unvalidated.forEach(t -> {
            t.setStatus(TaskStatus.UNCOMPLETED);
            t.setIsValidated(false);
            t.setIsSubmitted(false);
        });
        if (!unvalidated.isEmpty()) {
            taskRepository.saveAll(unvalidated);
            log.info("Marked {} stale unvalidated tasks UNCOMPLETED (cutoff {})",
                    unvalidated.size(), cutoff);
        }
    }

    @Override
    @Transactional
    public void updateDailyProductivity() {
        LocalDateTime since = LocalDate.now().minusDays(30).atStartOfDay();
        List<Personnel> all = personnelRepository.findAll().stream()
                .filter(p -> p.getDeletedAt() == null)
                .toList();

        int updated = 0;
        for (Personnel p : all) {
            List<Task> validated = taskRepository.findValidatedSince(p.getId(), since);
            double hours = 0.0;
            Set<LocalDate> days = new HashSet<>();
            for (Task t : validated) {
                if (t.getValidationTime() == null) continue;
                hours += (t.getActualHours() != null ? t.getActualHours() : 0.0);
                days.add(t.getValidationTime().toLocalDate());
            }

            boolean changed = false;
            if (p.getTotalHoursWorkedLast30Days() == null
                    || Math.abs(p.getTotalHoursWorkedLast30Days() - hours) > 0.001) {
                p.setTotalHoursWorkedLast30Days(hours);
                changed = true;
            }
            int dayCount = days.size();
            if (p.getTotalProductiveDays() == null || p.getTotalProductiveDays() != dayCount) {
                p.setTotalProductiveDays(dayCount);
                changed = true;
            }
            if (changed) {
                p.setLastProductivityUpdate(LocalDate.now());
                personnelRepository.save(p);
                updated++;
            }
        }
        log.info("Daily productivity updated — {} personnel scanned, {} changed", all.size(), updated);
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