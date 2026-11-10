package com.agriva.task.service.impl;

import com.agriva.shared.kafka.IdempotencyGuard;
import com.agriva.shared.kafka.topics.TaskKafkaConfig;
import com.agriva.task.model.dto.requests.TaskSubmissionEvent;
import com.agriva.task.model.entity.StagedTask;
import com.agriva.task.model.enums.StagedTaskStatus;
import com.agriva.task.repository.StagedTaskRepository;
import com.agriva.task.service.TaskStagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskStagingServiceImpl implements TaskStagingService {

    private final StagedTaskRepository stagedRepo;
    private final IdempotencyGuard idempotencyGuard;

    @Override
    public void persistStaged(TaskSubmissionEvent event) {
        if (event == null || event.getSubmissionId() == null || event.getPayloadJson() == null) {
            log.warn("Skipping malformed task event: {}", event);
            return;
        }

        if (!idempotencyGuard.tryMarkProcessed(
                event.getSubmissionId(), TaskKafkaConfig.STAGING_CONSUMER_GROUP)) {
            log.info("Duplicate delivery of task event {} — skipping", event.getSubmissionId());
            return;
        }

        StagedTask staged = StagedTask.builder()
                .id(event.getSubmissionId())
                .farmId(event.getFarmId())
                .payloadJson(event.getPayloadJson())
                .status(StagedTaskStatus.PENDING)
                .submittedByUserId(event.getSubmittedByUserId())
                .submittedByEmail(event.getSubmittedByEmail())
                .submittedAt(event.getSubmittedAt() != null
                        ? event.getSubmittedAt() : LocalDateTime.now())
                .retryCount(0)
                .build();
        try {
            stagedRepo.save(staged);
            log.info("Staged task {} persisted with status PENDING", staged.getId());
        } catch (DataIntegrityViolationException e) {
            log.error("Staged task {} FK violation, routing to DLQ: {}",
                    staged.getId(), e.getMessage());
            throw e;
        }
    }
}