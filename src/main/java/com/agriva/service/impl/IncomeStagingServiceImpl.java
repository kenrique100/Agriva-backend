package com.agriva.service.impl;

import com.agriva.model.dto.income.IncomeSubmissionEvent;
import com.agriva.model.entity.StagedIncome;
import com.agriva.model.entity.enums.StagedIncomeStatus;
import com.agriva.repository.StagedIncomeRepository;
import com.agriva.service.IncomeStagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncomeStagingServiceImpl implements IncomeStagingService {

    private final StagedIncomeRepository stagedRepo;

    @Override
    @Transactional
    public void persistStaged(IncomeSubmissionEvent event) {
        if (event == null || event.getSubmissionId() == null || event.getPayloadJson() == null) {
            log.warn("Skipping malformed income event: {}", event);
            return;
        }

        if (stagedRepo.existsById(event.getSubmissionId())) {
            log.info("Staged income {} already exists — skipping (idempotent)", event.getSubmissionId());
            return;
        }

        StagedIncome staged = StagedIncome.builder()
                .id(event.getSubmissionId())
                .farmId(event.getFarmId())
                .payloadJson(event.getPayloadJson())
                .status(StagedIncomeStatus.PENDING)
                .submittedByUserId(event.getSubmittedByUserId())
                .submittedByEmail(event.getSubmittedByEmail())
                .submittedAt(event.getSubmittedAt() != null ? event.getSubmittedAt() : LocalDateTime.now())
                .retryCount(0)
                .build();

        stagedRepo.save(staged);
        log.info("Staged income {} persisted with status PENDING", staged.getId());
    }
}