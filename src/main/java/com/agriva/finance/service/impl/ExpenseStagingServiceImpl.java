package com.agriva.finance.service.impl;

import com.agriva.finance.model.enums.StagedStatus;
import com.agriva.shared.kafka.IdempotencyGuard;
import com.agriva.shared.kafka.topics.ExpenseKafkaConfig;
import com.agriva.finance.model.dto.request.ExpenseSubmissionEvent;
import com.agriva.finance.model.entity.StagedExpense;
import com.agriva.finance.repository.StagedExpenseRepository;
import com.agriva.finance.service.ExpenseStagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseStagingServiceImpl implements ExpenseStagingService {

    private final StagedExpenseRepository stagedRepo;
    private final IdempotencyGuard idempotencyGuard;

    @Override
    @Transactional
    public void persistStaged(ExpenseSubmissionEvent event) {
        if (event == null || event.getSubmissionId() == null || event.getPayloadJson() == null) {
            log.warn("Skipping malformed expense event: {}", event);
            return;
        }

        if (!idempotencyGuard.tryMarkProcessed(
                event.getSubmissionId(), ExpenseKafkaConfig.STAGING_CONSUMER_GROUP)) {
            log.info("Duplicate delivery of expense event {} — skipping", event.getSubmissionId());
            return;
        }

        StagedExpense staged = StagedExpense.builder()
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
            log.info("Staged expense {} persisted with status PENDING (batch={}, row={})",
                    staged.getId(), staged.getBatchId(), staged.getRowNumber());
        } catch (DataIntegrityViolationException e) {
            log.error("Staged expense {} FK violation, routing to DLQ: {}",
                    staged.getId(), e.getMessage());
            throw e;
        }
    }
}