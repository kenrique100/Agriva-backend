package com.agriva.service.impl;

import com.agriva.model.dto.salary.SalarySubmissionEvent;
import com.agriva.model.entity.StagedSalary;
import com.agriva.model.entity.StagedSalaryStatus;
import com.agriva.repository.StagedSalaryRepository;
import com.agriva.service.SalaryStagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalaryStagingServiceImpl implements SalaryStagingService {

    private final StagedSalaryRepository stagedRepo;

    @Override
    @Transactional
    public void persistStaged(SalarySubmissionEvent event) {
        if (event == null || event.getSubmissionId() == null || event.getPayloadJson() == null) {
            log.warn("Skipping malformed salary event: {}", event);
            return;
        }

        // Idempotency — Kafka may deliver at-least-once
        if (stagedRepo.existsById(event.getSubmissionId())) {
            log.info("Staged salary {} already exists — skipping (idempotent)",
                    event.getSubmissionId());
            return;
        }

        StagedSalary staged = StagedSalary.builder()
                .id(event.getSubmissionId())
                .farmId(event.getFarmId())
                .payloadJson(event.getPayloadJson())
                .status(StagedSalaryStatus.PENDING)
                .submittedByUserId(event.getSubmittedByUserId())
                .submittedByEmail(event.getSubmittedByEmail())
                .submittedAt(event.getSubmittedAt() != null
                        ? event.getSubmittedAt() : LocalDateTime.now())
                .retryCount(0)
                .build();

        stagedRepo.save(staged);
        log.info("Staged salary {} persisted with status PENDING", staged.getId());
    }
}