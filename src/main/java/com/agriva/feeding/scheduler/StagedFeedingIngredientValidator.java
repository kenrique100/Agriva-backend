package com.agriva.feeding.scheduler;

import com.agriva.feeding.model.dto.request.IngredientRequest;
import com.agriva.feeding.model.dto.request.IngredientStockReplenishRequest;
import com.agriva.feeding.model.dto.request.IngredientSubmissionPayload;
import com.agriva.feeding.model.dto.response.IngredientResponse;
import com.agriva.feeding.model.entity.StagedFeedingIngredient;
import com.agriva.feeding.model.enums.StagedFeedingStatus;
import com.agriva.feeding.repository.StagedFeedingIngredientRepository;
import com.agriva.feeding.service.IngredientService;
import com.agriva.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class StagedFeedingIngredientValidator {

    private static final int ERROR_FIELD_LIMIT = 1990;

    private final StagedFeedingIngredientRepository stagedRepo;
    private final IngredientService ingredientService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;

    @Value("${agriva.feeding.ingredient.validator.batch-size:100}")
    private int batchSize;
    @Value("${agriva.feeding.ingredient.validator.max-attempts:4}")
    private int maxAttempts;
    @Value("${agriva.feeding.ingredient.validator.retry-delay-minutes:1}")
    private long retryDelayMinutes;

    @Scheduled(cron = "${agriva.feeding.ingredient.validator.cron:0 * * * * *}")
    public void processStaged() {
        List<StagedFeedingIngredient> due = stagedRepo.findDueForProcessing(
                LocalDateTime.now(),
                StagedFeedingStatus.PENDING,
                StagedFeedingStatus.RETRYING,
                PageRequest.of(0, batchSize));
        if (due.isEmpty()) return;
        log.info("StagedFeedingIngredientValidator: {} row(s) due", due.size());
        for (StagedFeedingIngredient s : due) {
            final UUID id = s.getId();
            txTemplate.execute(status -> { processOne(id); return null; });
        }
    }

    private void processOne(UUID id) {
        StagedFeedingIngredient staged = stagedRepo.findByIdForUpdate(id).orElse(null);
        if (staged == null || !isDue(staged, LocalDateTime.now())) return;
        staged.setLastAttemptAt(LocalDateTime.now());
        try {
            IngredientSubmissionPayload payload = objectMapper.readValue(
                    staged.getPayloadJson(), IngredientSubmissionPayload.class);

            IngredientRequest req = new IngredientRequest();
            req.setName(payload.name());
            req.setUnit(payload.unit());
            req.setCostPerUnit(payload.costPerUnit());
            req.setCurrency(payload.currency());
            req.setSupplier(payload.supplier());
            req.setDensityKgPerL(payload.densityKgPerL());

            IngredientResponse created = ingredientService.createIngredient(
                    req, staged.getFarmId(), staged.getSubmittedByUserId());

            if (payload.quantity() != null
                    && payload.quantity().compareTo(BigDecimal.ZERO) > 0) {
                IngredientStockReplenishRequest rep = new IngredientStockReplenishRequest();
                rep.setQuantity(payload.quantity());
                ingredientService.replenishStock(
                        created.getId(), rep, staged.getFarmId(), staged.getSubmittedByUserId());
            }

            staged.setStatus(StagedFeedingStatus.MOVED);
            staged.setMovedIngredientId(created.getId());
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            staged.setValidationErrors(null);
            stagedRepo.save(staged);
            log.info("Staged feeding-ingredient {} MOVED -> ingredient {}",
                    staged.getId(), created.getId());
        } catch (BusinessException e) {
            markFailed(staged, e.getMessage());
        } catch (Exception e) {
            log.error("Staged feeding-ingredient {} threw: {}", staged.getId(), e.getMessage(), e);
            onFailure(staged, List.of("Processing error: " + e.getMessage()));
        }
    }

    private void markFailed(StagedFeedingIngredient staged, String msg) {
        staged.setRetryCount((staged.getRetryCount() == null ? 0 : staged.getRetryCount()) + 1);
        staged.setValidationErrors(truncate(msg));
        staged.setStatus(StagedFeedingStatus.FAILED);
        staged.setProcessedAt(LocalDateTime.now());
        staged.setNextRetryAt(null);
        stagedRepo.save(staged);
    }

    private void onFailure(StagedFeedingIngredient staged, List<String> errors) {
        int attempt = (staged.getRetryCount() == null ? 0 : staged.getRetryCount()) + 1;
        staged.setRetryCount(attempt);
        staged.setValidationErrors(truncate(String.join("; ", errors)));
        if (attempt >= maxAttempts) {
            staged.setStatus(StagedFeedingStatus.FAILED);
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            log.error("Staged feeding-ingredient {} FAILED after {} attempts: {}",
                    staged.getId(), attempt, errors);
        } else {
            staged.setStatus(StagedFeedingStatus.RETRYING);
            staged.setNextRetryAt(LocalDateTime.now().plusMinutes(retryDelayMinutes));
            log.warn("Staged feeding-ingredient {} RETRY {}/{} — {}",
                    staged.getId(), attempt, maxAttempts, errors);
        }
        stagedRepo.save(staged);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= ERROR_FIELD_LIMIT ? s : s.substring(0, ERROR_FIELD_LIMIT);
    }

    private static boolean isDue(StagedFeedingIngredient s, LocalDateTime now) {
        if (s.getStatus() == StagedFeedingStatus.PENDING) return true;
        if (s.getStatus() != StagedFeedingStatus.RETRYING) return false;
        return s.getNextRetryAt() == null || !s.getNextRetryAt().isAfter(now);
    }
}