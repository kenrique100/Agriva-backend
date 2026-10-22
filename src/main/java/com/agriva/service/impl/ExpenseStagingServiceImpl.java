package com.agriva.service.impl;

import com.agriva.model.dto.expense.ExpenseSubmissionEvent;
import com.agriva.model.entity.StagedExpense;
import com.agriva.model.entity.enums.StagedExpenseStatus;
import com.agriva.repository.StagedExpenseRepository;
import com.agriva.service.ExpenseStagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseStagingServiceImpl implements ExpenseStagingService {

    private final StagedExpenseRepository stagedRepo;

    @Override
    @Transactional
    public void persistStaged(ExpenseSubmissionEvent event) {
        if (event == null || event.getSubmissionId() == null || event.getPayloadJson() == null) {
            log.warn("Skipping malformed expense event: {}", event);
            return;
        }

        if (stagedRepo.existsById(event.getSubmissionId())) {
            log.info("Staged expense {} already exists — skipping (idempotent)", event.getSubmissionId());
            return;
        }

        StagedExpense staged = StagedExpense.builder()
                .id(event.getSubmissionId())
                .farmId(event.getFarmId())
                .payloadJson(event.getPayloadJson())
                .status(StagedExpenseStatus.PENDING)
                .submittedByUserId(event.getSubmittedByUserId())
                .submittedByEmail(event.getSubmittedByEmail())
                .submittedAt(event.getSubmittedAt() != null ? event.getSubmittedAt() : LocalDateTime.now())
                .retryCount(0)
                .build();

        stagedRepo.save(staged);
        log.info("Staged expense {} persisted with status PENDING", staged.getId());
    }
}