package com.agriva.shared.security;

import com.agriva.personnel.models.entity.Personnel;
import com.agriva.personnel.repository.PersonnelRepository;
import com.agriva.task.model.entity.Task;
import com.agriva.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("taskAuthorization")
@RequiredArgsConstructor
public class TaskAuthorization {

    private final TaskRepository taskRepository;
    private final PersonnelRepository personnelRepository;

    public boolean canAct(UUID taskId, AuthenticatedUser principal) {
        if (principal == null) return false;
        if (principal.isMasterOrAdmin()) return true;

        Task task = taskRepository.findByIdAndFarmIdLive(taskId, principal.farmId())
                .orElse(null);
        if (task == null) return false;

        Personnel own = personnelRepository.findByUserIdLive(principal.userId())
                .orElse(null);
        return own != null && own.getId().equals(task.getPersonnelId());
    }
}