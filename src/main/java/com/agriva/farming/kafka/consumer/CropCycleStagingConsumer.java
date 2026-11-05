package com.agriva.farming.kafka.consumer;

import com.agriva.shared.kafka.IdempotencyGuard;
import com.agriva.shared.kafka.topics.CropKafkaConfig;
import com.agriva.farming.model.dto.requests.CropCycleSubmissionEvent;
import com.agriva.farming.model.entity.StagedCropCycle;
import com.agriva.farming.model.enums.StagedCropCycleStatus;
import com.agriva.farming.repository.StagedCropCycleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public void onCropCycleSubmitted(CropCycleSubmissionEvent event) {
        if (event == null || event.getSubmissionId() == null
                || event.getPayloadJson() == null) {
            log.warn("Skipping malformed crop cycle event: {}", event);
            return;
        }

        if (!idempotencyGuard.tryMarkProcessed(
                event.getSubmissionId(), CropKafkaConfig.STAGING_CONSUMER_GROUP)) {
            log.info("Duplicate delivery of crop cycle event {} — skipping",
                    event.getSubmissionId());
            return;
        }

        StagedCropCycle staged = StagedCropCycle.builder()
                .id(event.getSubmissionId())
                .farmId(event.getFarmId())
                .payloadJson(event.getPayloadJson())
                .status(StagedCropCycleStatus.PENDING)
                .submittedByUserId(event.getSubmittedByUserId())
                .submittedByEmail(event.getSubmittedByEmail())
                .submittedAt(event.getSubmittedAt() != null
                        ? event.getSubmittedAt() : LocalDateTime.now())
                .retryCount(0)
                .build();

        stagedRepo.save(staged);
        log.info("Staged crop cycle {} persisted", staged.getId());
    }
}