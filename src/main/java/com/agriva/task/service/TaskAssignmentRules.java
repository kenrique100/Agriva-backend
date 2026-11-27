package com.agriva.task.service;

import com.agriva.personnel.models.entity.Personnel;
import com.agriva.personnel.models.enums.PersonnelStatus;
import com.agriva.personnel.repository.PersonnelRepository;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.PersonnelNotFoundException;
import com.agriva.task.model.entity.Task;
import com.agriva.task.model.enums.TaskStatus;
import com.agriva.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TaskAssignmentRules {

    private final TaskRepository taskRepository;
    private final PersonnelRepository personnelRepository;

    public Personnel validateAssignable(UUID personnelId, UUID farmId, UUID excludeTaskId) {
        Personnel p = personnelRepository.findByIdAndFarmIdLive(personnelId, farmId)
                .orElseThrow(() -> new PersonnelNotFoundException(
                        "Personnel not found with ID: " + personnelId));

        if (Boolean.TRUE.equals(p.getIsFarmOwner())) {
            throw new BusinessException("The farm owner cannot be assigned tasks.");
        }
        if (p.getStatus() != PersonnelStatus.ACTIVE) {
            throw new BusinessException(
                    "Cannot assign tasks to personnel who are not ACTIVE (current: "
                            + p.getStatus() + ").");
        }

        Optional<Task> open = findOpenTask(personnelId, excludeTaskId);
        if (open.isPresent()) {
            throw new BusinessException(String.format(
                    "%s already has an open task ('%s'). Finish or cancel it first.",
                    p.getName(), open.get().getTitle()));
        }
        return p;
    }


    public String validateAssignableOrError(UUID personnelId, UUID farmId, UUID excludeTaskId) {
        try {
            validateAssignable(personnelId, farmId, excludeTaskId);
            return null;
        } catch (BusinessException | PersonnelNotFoundException e) {
            return e.getMessage();
        }
    }

    public Optional<Task> findOpenTask(UUID personnelId, UUID excludeTaskId) {
        List<Task> open = taskRepository.findOpenTasksForPersonnel(
                personnelId, excludeTaskId, TaskStatus.openStatuses());
        return open.stream().findFirst();
    }
}