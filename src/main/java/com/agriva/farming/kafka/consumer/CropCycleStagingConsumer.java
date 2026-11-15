package com.agriva.farming.kafka.consumer;

import com.agriva.shared.kafka.IdempotencyGuard;
import com.agriva.shared.kafka.topics.CropKafkaConfig;
import com.agriva.farming.model.dto.requests.CropCycleSubmissionEvent;
import com.agriva.farming.model.entity.StagedCropCycle;
import com.agriva.farming.model.entity.StagedCropCycleId;
import com.agriva.farming.model.enums.StagedCropCycleStatus;
import com.agriva.farming.repository.StagedCropCycleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class CropCycleStagingConsumer {

    private final StagedCropCycleRepository stagedRepo;
    private final IdempotencyGuard idempotencyGuard;

    @KafkaListener(
            topics = CropKafkaConfig.STAGING_TOPIC,
            groupId = CropKafkaConfig.STAGING_CONSUMER_GROUP,
            containerFactory = "cropKafkaListenerContainerFactory")
    public void onCropCycleSubmitted(CropCycleSubmissionEvent event) {
        if (event == null || event.getSubmissionId() == null
                || event.getPayloadJson() == null) {
            log.warn("Skipping malformed crop cycle event: {}", event);
            return;
        }

        LocalDateTime submittedAt = event.getSubmittedAt() != null
                ? event.getSubmittedAt()
                : LocalDateTime.now();

        StagedCropCycleId pk = new StagedCropCycleId(event.getSubmissionId(), submittedAt);

        // Idempotency: composite PK already exists → duplicate delivery
        if (stagedRepo.existsById(pk)) {
            log.info("Duplicate delivery of crop cycle event {} — skipping",
                    event.getSubmissionId());
            return;
        }

        StagedCropCycle staged = StagedCropCycle.builder()
                .id(event.getSubmissionId())
                .submittedAt(submittedAt)
                .farmId(event.getFarmId())
                .payloadJson(event.getPayloadJson())
                .status(StagedCropCycleStatus.PENDING)
                .submittedByUserId(event.getSubmittedByUserId())
                .submittedByEmail(event.getSubmittedByEmail())
                .retryCount(0)
                .build();

        try {
            stagedRepo.saveAndFlush(staged);
        } catch (DataIntegrityViolationException e) {
            // Race with another consumer instance → treat as duplicate, not error.
            log.warn("Duplicate insert for crop cycle event {} (idempotent): {}",
                    event.getSubmissionId(), e.getMessage());
            return;
        }

        // Only mark processed AFTER a successful save.
        idempotencyGuard.tryMarkProcessed(
                event.getSubmissionId(), CropKafkaConfig.STAGING_CONSUMER_GROUP);

        log.info("Staged crop cycle {} persisted", staged.getId());
    }
}