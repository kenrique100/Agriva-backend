package com.agriva.service;

import com.agriva.model.dto.task.TaskSubmissionEvent;

public interface TaskStagingService {
    void persistStaged(TaskSubmissionEvent event);
}