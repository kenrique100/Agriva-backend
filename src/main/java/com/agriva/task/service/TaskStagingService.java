package com.agriva.task.service;

import com.agriva.task.model.dto.requests.TaskSubmissionEvent;

public interface TaskStagingService {
    void persistStaged(TaskSubmissionEvent event);
}