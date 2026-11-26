package com.agriva.payroll.service.impl;

import com.agriva.payroll.model.dto.requests.SalarySubmissionEvent;
import com.agriva.payroll.model.entity.StagedSalary;
import com.agriva.payroll.model.enums.StagedSalaryStatus;
import com.agriva.payroll.repository.StagedSalaryRepository;
import com.agriva.payroll.service.SalaryStagingService;
import com.agriva.shared.kafka.IdempotencyGuard;
import com.agriva.shared.kafka.topics.SalaryKafkaConfig;
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
    private final IdempotencyGuard idempotencyGuard;

    @Override
    @Transactional
    public void persistStaged(SalarySubmissionEvent event) {
        if (event == null || event.getSubmissionId() == null || event.getPayloadJson() == null) {
            log.warn("Skipping malformed salary event: {}", event);
            return;
        }

        if (!idempotencyGuard.tryMarkProcessed(
                event.getSubmissionId(), SalaryKafkaConfig.STAGING_CONSUMER_GROUP)) {
            log.info("Duplicate delivery of salary event {} — skipping", event.getSubmissionId());
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