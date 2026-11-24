package com.agriva.task.mapper;

import com.agriva.shared.port.PersonnelLookupPort;
import com.agriva.task.model.dto.response.TaskResponse;
import com.agriva.task.model.entity.Task;
import com.agriva.task.model.enums.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TaskMapper {

    private final PersonnelLookupPort personnelLookupPort;

    public TaskResponse toResponse(Task t) {
        String personnelName = t.getPersonnelId() == null ? null
                : personnelLookupPort.findByIdAndFarmId(t.getPersonnelId(), t.getFarmId())
                  .map(PersonnelLookupPort.PersonnelInfo::name)
                  .orElse("Unknown");

        return build(t, personnelName);
    }

    public TaskResponse toResponse(Task t, Map<UUID, String> personnelNameById) {
        String personnelName = t.getPersonnelId() == null ? null
                : personnelNameById.getOrDefault(t.getPersonnelId(), "Unknown");
        return build(t, personnelName);
    }

    private TaskResponse build(Task t, String personnelName) {
        return new TaskResponse(
                t.getId(),
                t.getFarmId(),
                t.getPersonnelId(),
                personnelName,
                t.getTitle(),
                t.getDescription(),
                t.getDeadline(),
                t.getStatus(),
                t.getExpectedHours(),
                t.getActualHours(),
                t.getTotalWorkedMinutes(),
                t.getStartTime(),
                t.getStopTime(),
                t.getLastResumeTime(),
                t.getIsValidated(),
                t.getValidationTime(),
                t.getIsSubmitted(),
                t.getAssignedByUserId(),
                t.getValidatedByUserId(),
                t.getCreatedAt(),
                t.getUpdatedAt(),
                computeRemainingSeconds(t)
        );
    }

    private Long computeRemainingSeconds(Task t) {
        if (t.getStatus() != TaskStatus.IN_PROGRESS) return null;
        if (t.getExpectedHours() == null || t.getExpectedHours() <= 0) return null;
        if (t.getLastResumeTime() == null) return null;

        long expectedSeconds = t.getExpectedHours() * 3600L;
        long workedSeconds = (t.getTotalWorkedMinutes() == null ? 0L : t.getTotalWorkedMinutes()) * 60L;
        long currentSegmentSeconds = Math.max(0L,
                Duration.between(t.getLastResumeTime(), LocalDateTime.now()).getSeconds());

        // Can go negative when the task is in overtime.
        return expectedSeconds - workedSeconds - currentSegmentSeconds;
    }
}