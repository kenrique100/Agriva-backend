package com.agriva.feeding.scheduler;

import com.agriva.feeding.model.dto.request.ProductionRequest;
import com.agriva.feeding.model.dto.request.ProductionSubmissionPayload;
import com.agriva.feeding.model.dto.response.FeedBatchResponse;
import com.agriva.feeding.model.entity.StagedFeedingProduction;
import com.agriva.feeding.model.enums.StagedFeedingStatus;
import com.agriva.feeding.repository.FormulationRepository;
import com.agriva.feeding.repository.StagedFeedingProductionRepository;
import com.agriva.feeding.service.ProductionService;
import com.agriva.shared.exception.InsufficientIngredientQuantityException;
import com.agriva.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StagedFeedingProductionValidator {

    private static final int ERROR_FIELD_LIMIT = 1990;

    private final StagedFeedingProductionRepository stagedRepo;
    private final FormulationRepository formulationRepo;
    private final ProductionService productionService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;

    @Value("${agriva.feeding.production.validator.batch-size:100}")
    private int batchSize;
    @Value("${agriva.feeding.production.validator.max-attempts:4}")
    private int maxAttempts;
    @Value("${agriva.feeding.production.validator.retry-delay-minutes:1}")
    private long retryDelayMinutes;

    @Scheduled(cron = "${agriva.feeding.production.validator.cron:0 * * * * *}")
    public void processStaged() {
        List<StagedFeedingProduction> due = stagedRepo.findDueForProcessing(
                LocalDateTime.now(),
                StagedFeedingStatus.PENDING,
                StagedFeedingStatus.RETRYING,
                PageRequest.of(0, batchSize));
        if (due.isEmpty()) return;
        log.info("StagedFeedingProductionValidator: {} row(s) due", due.size());
        for (StagedFeedingProduction s : due) {
            final UUID id = s.getId();
            txTemplate.execute(status -> { processOne(id); return null; });
        }
    }

    private void processOne(UUID id) {
        StagedFeedingProduction staged = stagedRepo.findByIdForUpdate(id).orElse(null);
        if (staged == null || !isDue(staged, LocalDateTime.now())) return;
        staged.setLastAttemptAt(LocalDateTime.now());
        try {
            ProductionSubmissionPayload payload = objectMapper.readValue(
                    staged.getPayloadJson(), ProductionSubmissionPayload.class);

            var formulation = formulationRepo
                    .findByFarmIdAndNameLive(staged.getFarmId(), payload.formulationName())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Formulation not found: " + payload.formulationName()));

            ProductionRequest req = new ProductionRequest();
            req.setFormulationId(formulation.getId());
            req.setProducedKg(payload.producedKg());
            req.setProductionDate(payload.productionDate());
            req.setNotes(payload.notes());

            FeedBatchResponse created = productionService.produce(
                    req, staged.getFarmId(), staged.getSubmittedByUserId());

            staged.setStatus(StagedFeedingStatus.MOVED);
            staged.setMovedBatchId(created.getId());
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            staged.setValidationErrors(null);
            stagedRepo.save(staged);
            log.info("Staged feeding-production {} MOVED -> batch {}",
                    staged.getId(), created.getId());
        } catch (InsufficientIngredientQuantityException e) {
            onFailure(staged, List.of(e.getMessage()));
        } catch (ResourceNotFoundException e) {
            markFailed(staged, e.getMessage());
        } catch (Exception e) {
            log.error("Staged feeding-production {} threw: {}",
                    staged.getId(), e.getMessage(), e);
            onFailure(staged, List.of("Processing error: " + e.getMessage()));
        }
    }

    private void markFailed(StagedFeedingProduction staged, String msg) {
        staged.setRetryCount((staged.getRetryCount() == null ? 0 : staged.getRetryCount()) + 1);
        staged.setValidationErrors(truncate(msg));
        staged.setStatus(StagedFeedingStatus.FAILED);
        staged.setProcessedAt(LocalDateTime.now());
        staged.setNextRetryAt(null);
        stagedRepo.save(staged);
    }

    private void onFailure(StagedFeedingProduction staged, List<String> errors) {
        int attempt = (staged.getRetryCount() == null ? 0 : staged.getRetryCount()) + 1;
        staged.setRetryCount(attempt);
        staged.setValidationErrors(truncate(String.join("; ", errors)));
        if (attempt >= maxAttempts) {
            staged.setStatus(StagedFeedingStatus.FAILED);
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            log.error("Staged feeding-production {} FAILED after {} attempts: {}",
                    staged.getId(), attempt, errors);
        } else {
            staged.setStatus(StagedFeedingStatus.RETRYING);
            staged.setNextRetryAt(LocalDateTime.now().plusMinutes(retryDelayMinutes));
            log.warn("Staged feeding-production {} RETRY {}/{} — {}",
                    staged.getId(), attempt, maxAttempts, errors);
        }
        stagedRepo.save(staged);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= ERROR_FIELD_LIMIT ? s : s.substring(0, ERROR_FIELD_LIMIT);
    }

    private static boolean isDue(StagedFeedingProduction s, LocalDateTime now) {
        if (s.getStatus() == StagedFeedingStatus.PENDING) return true;
        if (s.getStatus() != StagedFeedingStatus.RETRYING) return false;
        return s.getNextRetryAt() == null || !s.getNextRetryAt().isAfter(now);
    }
}