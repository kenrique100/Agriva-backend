package com.agriva.service.impl;

import com.agriva.config.TaskKafkaConfig;
import com.agriva.kafka.consumer.IdempotencyGuard;
import com.agriva.model.dto.task.TaskSubmissionEvent;
import com.agriva.model.entity.StagedTask;
import com.agriva.model.entity.enums.StagedTaskStatus;
import com.agriva.repository.StagedTaskRepository;
import com.agriva.service.TaskStagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskStagingServiceImpl implements TaskStagingService {

    private final StagedTaskRepository stagedRepo;
    private final IdempotencyGuard idempotencyGuard;

    @Override
    @Transactional
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
        stagedRepo.save(staged);
        log.info("Staged task {} persisted with status PENDING", staged.getId());
    }
}