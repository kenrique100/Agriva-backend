package com.agriva.finance.service.impl;

import com.agriva.shared.kafka.IdempotencyGuard;
import com.agriva.shared.kafka.topics.IncomeKafkaConfig;
import com.agriva.finance.model.dto.request.IncomeSubmissionEvent;
import com.agriva.finance.model.entity.StagedIncome;
import com.agriva.finance.model.enums.StagedIncomeStatus;
import com.agriva.finance.repository.StagedIncomeRepository;
import com.agriva.finance.service.IncomeStagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncomeStagingServiceImpl implements IncomeStagingService {

    private final StagedIncomeRepository stagedRepo;
    private final IdempotencyGuard idempotencyGuard;

    @Override
    public void persistStaged(IncomeSubmissionEvent event) {
        if (event == null || event.getSubmissionId() == null || event.getPayloadJson() == null) {
            log.warn("Skipping malformed income event: {}", event);
            return;
        }

        if (!idempotencyGuard.tryMarkProcessed(
                event.getSubmissionId(), IncomeKafkaConfig.STAGING_CONSUMER_GROUP)) {
            log.info("Duplicate delivery of income event {} — skipping", event.getSubmissionId());
            return;
        }

        StagedIncome staged = StagedIncome.builder()
                .id(event.getSubmissionId())
                .farmId(event.getFarmId())
                .payloadJson(event.getPayloadJson())
                .status(StagedIncomeStatus.PENDING)
                .submittedByUserId(event.getSubmittedByUserId())
                .submittedByEmail(event.getSubmittedByEmail())
                .submittedAt(event.getSubmittedAt() != null
                        ? event.getSubmittedAt() : LocalDateTime.now())
                .retryCount(0)
                .build();
        try {
            stagedRepo.save(staged);
            log.info("Staged income {} persisted with status PENDING", staged.getId());
        } catch (DataIntegrityViolationException e) {
            log.error("Staged income {} FK violation, routing to DLQ: {}",
                    staged.getId(), e.getMessage());
            throw e;
        }
    }
}