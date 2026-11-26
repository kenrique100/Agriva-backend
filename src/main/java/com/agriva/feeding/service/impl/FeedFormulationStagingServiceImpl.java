package com.agriva.feeding.service.impl;

import com.agriva.feeding.model.dto.request.FeedFormulationSubmissionEvent;
import com.agriva.feeding.model.entity.StagedFeedingFormulation;
import com.agriva.feeding.repository.StagedFeedingFormulationRepository;
import com.agriva.feeding.service.FeedFormulationStagingService;
import com.agriva.shared.kafka.IdempotencyGuard;
import com.agriva.shared.kafka.topics.FeedFormulationKafkaConfig;
import com.agriva.shared.models.enums.StagedStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedFormulationStagingServiceImpl implements FeedFormulationStagingService {

    private final StagedFeedingFormulationRepository stagedRepo;
    private final IdempotencyGuard idempotencyGuard;

    @Override
    @Transactional
    public boolean persistStaged(FeedFormulationSubmissionEvent event) {
        if (event == null || event.getSubmissionId() == null || event.getPayloadJson() == null) {
            log.warn("Skipping malformed feeding-formulation event: {}", event);
            return false;
        }

        if (!idempotencyGuard.tryMarkProcessed(
                event.getSubmissionId(),
                FeedFormulationKafkaConfig.STAGING_CONSUMER_GROUP)) {
            log.info("Duplicate delivery of feeding-formulation event {} — skipping",
                    event.getSubmissionId());
            return false;
        }

        StagedFeedingFormulation staged = StagedFeedingFormulation.builder()
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

        stagedRepo.save(staged);
        log.info("Staged feeding-formulation {} persisted (batch={}, row={})",
                staged.getId(), staged.getBatchId(), staged.getRowNumber());
        return true;
    }
}