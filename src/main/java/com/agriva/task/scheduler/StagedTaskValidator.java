package com.agriva.task.scheduler;

import com.agriva.personnel.models.entity.Personnel;
import com.agriva.personnel.models.enums.PersonnelStatus;
import com.agriva.personnel.repository.PersonnelRepository;
import com.agriva.task.model.dto.requests.TaskCreateRequest;
import com.agriva.task.model.entity.StagedTask;
import com.agriva.task.model.entity.Task;
import com.agriva.task.model.enums.StagedTaskStatus;
import com.agriva.task.model.enums.TaskStatus;
import com.agriva.task.repository.StagedTaskRepository;
import com.agriva.task.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StagedTaskValidator {

    private static final int ERROR_FIELD_LIMIT = 1990;

    private final StagedTaskRepository stagedRepo;
    private final TaskRepository taskRepo;
    private final PersonnelRepository personnelRepo;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${agriva.task.validator.batch-size:100}")
    private int batchSize;
    @Value("${agriva.task.validator.max-attempts:4}")
    private int maxAttempts;
    @Value("${agriva.task.validator.retry-delay-minutes:1}")
    private long retryDelayMinutes;

    @Scheduled(cron = "${agriva.task.validator.cron:0 * * * * *}")
    public void processStagedTasks() {
        List<StagedTask> due = stagedRepo.findDueForProcessing(
                LocalDateTime.now(),
                StagedTaskStatus.PENDING,
                StagedTaskStatus.RETRYING,
                PageRequest.of(0, batchSize));
        if (due.isEmpty()) return;

        log.info("StagedTaskValidator: {} row(s) due for processing", due.size());
        for (StagedTask staged : due) {
            UUID stagedId = staged.getId();
            transactionTemplate.execute(status -> { processOne(stagedId); return null; });
        }
    }

    private void processOne(UUID stagedId) {
        List<StagedTask> found = stagedRepo.findByIdForUpdate(stagedId);
        if (found.isEmpty()) return;
        StagedTask staged = found.get(0);

        LocalDateTime now = LocalDateTime.now();
        if (!isDueForProcessing(staged, now)) return;

        staged.setLastAttemptAt(now);
        try {
            TaskCreateRequest request = objectMapper.readValue(
                    staged.getPayloadJson(), TaskCreateRequest.class);

            String error = validate(request, staged.getFarmId());
            if (error != null) { onFailure(staged, List.of(error)); return; }

            Personnel p = personnelRepo
                    .findByIdAndFarmIdLive(request.getPersonnelId(), staged.getFarmId())
                    .orElseThrow(() -> new IllegalStateException("Personnel missing"));

            Task t = Task.builder()
                    .farmId(staged.getFarmId())
                    .personnelId(p.getId())
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .deadline(request.getDeadline())
                    .expectedHours(request.getExpectedHours())
                    .status(TaskStatus.PENDING)
                    .assignedByUserId(staged.getSubmittedByUserId())
                    .build();
            Task saved = taskRepo.save(t);

            staged.setStatus(StagedTaskStatus.MOVED);
            staged.setMovedTaskId(saved.getId());
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            staged.setValidationErrors(null);
            stagedRepo.save(staged);

            log.info("Staged task {} MOVED -> task {}", staged.getId(), saved.getId());
        } catch (Exception e) {
            log.error("Staged task {} threw: {}", staged.getId(), e.getMessage(), e);
            onFailure(staged, List.of("Processing error: " + e.getMessage()));
        }
    }

    private String validate(TaskCreateRequest req, UUID farmId) {
        if (req.getPersonnelId() == null) return "Personnel is required";
        if (req.getTitle() == null || req.getTitle().isBlank()) return "Title is required";
        if (req.getDeadline() == null) return "Deadline is required";
        if (req.getDeadline().isBefore(LocalDate.now())) return "Deadline cannot be in the past";
        if (req.getExpectedHours() == null || req.getExpectedHours() <= 0)
            return "Expected hours must be positive";

        Personnel p = personnelRepo.findByIdAndFarmIdLive(req.getPersonnelId(), farmId)
                .orElse(null);
        if (p == null) return "Personnel not found in farm";
        if (p.getStatus() != PersonnelStatus.ACTIVE)
            return "Cannot assign tasks to personnel who are not ACTIVE (current: "
                    + p.getStatus() + ")";
        return null;
    }

    private void onFailure(StagedTask staged, List<String> errors) {
        int attempt = (staged.getRetryCount() == null ? 0 : staged.getRetryCount()) + 1;
        staged.setRetryCount(attempt);
        staged.setValidationErrors(truncate(String.join("; ", errors)));
        if (attempt >= maxAttempts) {
            staged.setStatus(StagedTaskStatus.FAILED);
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            log.error("Staged task {} FAILED after {} attempt(s): {}", staged.getId(), attempt, errors);
        } else {
            staged.setStatus(StagedTaskStatus.RETRYING);
            staged.setNextRetryAt(LocalDateTime.now().plusMinutes(retryDelayMinutes));
            log.warn("Staged task {} RETRY {}/{} — {}", staged.getId(), attempt, maxAttempts, errors);
        }
        stagedRepo.save(staged);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= ERROR_FIELD_LIMIT ? s : s.substring(0, ERROR_FIELD_LIMIT);
    }

    private static boolean isDueForProcessing(StagedTask staged, LocalDateTime now) {
        if (staged.getStatus() == StagedTaskStatus.PENDING) return true;
        if (staged.getStatus() != StagedTaskStatus.RETRYING) return false;
        return staged.getNextRetryAt() == null || !staged.getNextRetryAt().isAfter(now);
    }
}