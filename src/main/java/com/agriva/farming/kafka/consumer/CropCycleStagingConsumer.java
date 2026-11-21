package com.agriva.farming.kafka.consumer;

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

        if (stagedRepo.existsById(pk)) {
            log.info("Duplicate delivery of crop cycle event {} — skipping", event.getSubmissionId());
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
            log.info("Staged crop cycle {} persisted", staged.getId());
        } catch (DataIntegrityViolationException e) {
            // Race condition: another consumer instance inserted it milliseconds ago.
            log.warn("Duplicate insert for crop cycle event {} (idempotent): {}",
                    event.getSubmissionId(), e.getMessage());
        }
    }
}