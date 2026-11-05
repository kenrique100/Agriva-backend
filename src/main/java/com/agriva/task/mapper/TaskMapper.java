package com.agriva.task.mapper;

import com.agriva.task.model.dto.response.TaskResponse;
import com.agriva.task.model.entity.Task;
import org.springframework.stereotype.Component;

@Component
public class TaskMapper {

    public TaskResponse toResponse(Task t) {
        return new TaskResponse(
                t.getId(),
                t.getFarmId(),
                t.getPersonnel() != null ? t.getPersonnel().getId() : null,
                t.getPersonnel() != null ? t.getPersonnel().getName() : null,
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
                t.getUpdatedAt()
        );
    }
}