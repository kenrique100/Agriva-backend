package com.agriva.task.service;

import com.agriva.task.model.dto.requests.TaskCreateRequest;
import com.agriva.task.model.dto.requests.TaskUpdateRequest;
import com.agriva.task.model.dto.response.ProductivityStatsResponse;
import com.agriva.task.model.dto.response.StagedTaskResponse;
import com.agriva.task.model.dto.response.TaskBulkUploadResponse;
import com.agriva.task.model.dto.response.TaskResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

public interface TaskService {
    TaskResponse create(TaskCreateRequest request, UUID farmId, UUID assignedByUserId);

    Page<TaskResponse> list(UUID farmId, UUID personnelId, String status, Pageable pageable);

    TaskResponse get(UUID id, UUID farmId);
    TaskResponse update(UUID id, TaskUpdateRequest request, UUID farmId);
    TaskResponse action(UUID id, UUID farmId, UUID actorUserId, boolean isMaster, String action);
    TaskResponse validate(UUID id, UUID farmId, UUID adminUserId, boolean approve);
    void delete(UUID id, UUID farmId);
    ProductivityStatsResponse productivity(UUID personnelId, UUID farmId);
    TaskBulkUploadResponse bulkUpload(MultipartFile file, UUID farmId, UUID userId, String email);
    Page<StagedTaskResponse> listStaging(UUID farmId, String status, Pageable pageable);
    Map<String, Long> stagingStats(UUID farmId);
    void processExpiredTasks();
    void processUnvalidatedTasks();
    void updateDailyProductivity();
    void resetProductivityMetrics();
}