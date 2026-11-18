package com.agriva.feeding.service.impl;

import com.agriva.feeding.model.dto.request.FeedIngredientSubmissionEvent;
import com.agriva.feeding.model.entity.StagedFeedingIngredient;
import com.agriva.feeding.repository.StagedFeedingIngredientRepository;
import com.agriva.feeding.service.IngredientStagingService;
import com.agriva.shared.kafka.IdempotencyGuard;
import com.agriva.shared.kafka.topics.FeedIngredientKafkaConfig;
import com.agriva.shared.models.enums.StagedStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngredientStagingServiceImpl implements IngredientStagingService {

    private final StagedFeedingIngredientRepository stagedRepo;
    private final IdempotencyGuard idempotencyGuard;

    @Override
    @Transactional
    public boolean persistStaged(FeedIngredientSubmissionEvent event) {
        if (event == null
                || event.getSubmissionId() == null
                || event.getPayloadJson() == null) {
            log.warn("Skipping malformed feeding-ingredient event: {}", event);
            return false;
        }

        if (!idempotencyGuard.tryMarkProcessed(
                event.getSubmissionId(), FeedIngredientKafkaConfig.STAGING_CONSUMER_GROUP)) {
            log.info("Duplicate delivery of feeding-ingredient event {} — skipping",
                    event.getSubmissionId());
            return false;
        }

        StagedFeedingIngredient staged = StagedFeedingIngredient.builder()
                .id(event.getSubmissionId())
                .farmId(event.getFarmId())
                .payloadJson(event.getPayloadJson())
                .status(StagedStatus.PENDING)
                .submittedByUserId(event.getSubmittedByUserId())
                .submittedByEmail(event.getSubmittedByEmail())
                .submittedAt(event.getSubmittedAt() != null
                        ? event.getSubmittedAt() : LocalDateTime.now())
                .batchId(event.getBatchId())
                .rowNumber(event.getRowNumber())
                .retryCount(0)
                .build();

        try {
            stagedRepo.save(staged);
            log.info("Staged feeding-ingredient {} persisted with status PENDING (batch={}, row={})",
                    staged.getId(), staged.getBatchId(), staged.getRowNumber());
            return true;
        } catch (DataIntegrityViolationException e) {
            log.error("Staged feeding-ingredient {} FK violation, routing to DLQ: {}",
                    staged.getId(), e.getMessage());
            throw e;
        }
    }
}