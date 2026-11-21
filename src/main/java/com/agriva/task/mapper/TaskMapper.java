package com.agriva.task.mapper;

import com.agriva.shared.port.PersonnelLookupPort;
import com.agriva.task.model.dto.response.TaskResponse;
import com.agriva.task.model.entity.Task;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskMapper {

    private final PersonnelLookupPort personnelLookupPort;

    public TaskResponse toResponse(Task t) {
        String personnelName = t.getPersonnelId() == null ? null
                : personnelLookupPort.findByIdAndFarmId(t.getPersonnelId(), t.getFarmId())
                  .map(PersonnelLookupPort.PersonnelInfo::name)
                  .orElse("Unknown");

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
                t.getUpdatedAt()
        );
    }
}