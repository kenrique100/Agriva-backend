package com.agriva.feeding.scheduler;

import com.agriva.feeding.model.dto.request.FormulationRequest;
import com.agriva.feeding.model.dto.request.FormulationSubmissionPayload;
import com.agriva.feeding.model.dto.response.FormulationResponse;
import com.agriva.feeding.model.entity.StagedFeedingFormulation;
import com.agriva.feeding.model.enums.StagedFeedingStatus;
import com.agriva.feeding.repository.IngredientRepository;
import com.agriva.feeding.repository.StagedFeedingFormulationRepository;
import com.agriva.feeding.service.FormulationService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.InsufficientIngredientQuantityException;
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
public class StagedFeedingFormulationValidator {

    private static final int ERROR_FIELD_LIMIT = 1990;

    private final StagedFeedingFormulationRepository stagedRepo;
    private final IngredientRepository ingredientRepo;
    private final FormulationService formulationService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;

    @Value("${agriva.feeding.formulation.validator.batch-size:100}")
    private int batchSize;
    @Value("${agriva.feeding.formulation.validator.max-attempts:4}")
    private int maxAttempts;
    @Value("${agriva.feeding.formulation.validator.retry-delay-minutes:1}")
    private long retryDelayMinutes;

    @Scheduled(cron = "${agriva.feeding.formulation.validator.cron:0 * * * * *}")
    public void processStaged() {
        List<StagedFeedingFormulation> due = stagedRepo.findDueForProcessing(
                LocalDateTime.now(),
                StagedFeedingStatus.PENDING,
                StagedFeedingStatus.RETRYING,
                PageRequest.of(0, batchSize));
        if (due.isEmpty()) return;
        log.info("StagedFeedingFormulationValidator: {} row(s) due", due.size());
        for (StagedFeedingFormulation s : due) {
            final UUID id = s.getId();
            txTemplate.execute(status -> { processOne(id); return null; });
        }
    }

    public void processOneImmediately(UUID id) {
        txTemplate.execute(status -> { processOne(id); return null; });
    }

    private void processOne(UUID id) {
        StagedFeedingFormulation staged = stagedRepo.findByIdForUpdate(id).orElse(null);
        if (staged == null || !isDue(staged, LocalDateTime.now())) return;
        staged.setLastAttemptAt(LocalDateTime.now());
        try {
            FormulationSubmissionPayload payload = objectMapper.readValue(
                    staged.getPayloadJson(), FormulationSubmissionPayload.class);

            FormulationRequest req = new FormulationRequest();
            req.setName(payload.name());
            req.setTargetSpecies(payload.targetSpecies());
            req.setMode(payload.mode());
            req.setTargetQuantityKg(payload.targetQuantityKg());
            req.setTargetCpValue(payload.targetCpValue());

            if (payload.lines() != null && !payload.lines().isEmpty()) {
                final UUID farmId = staged.getFarmId();
                var resolvedLines = payload.lines().stream().map(l -> {
                    var ingredient = ingredientRepo
                            .findByFarmIdAndNameLive(farmId, l.ingredientName())
                            .orElseThrow(() -> new BusinessException(
                                    "Ingredient not found on farm: " + l.ingredientName()));
                    FormulationRequest.Line fLine = new FormulationRequest.Line();
                    fLine.setIngredientId(ingredient.getId());
                    fLine.setQuantityKg(l.quantityKg());
                    return fLine;
                }).toList();
                req.setLines(resolvedLines);
            }

            FormulationResponse created = formulationService.createFormulation(
                    req, staged.getFarmId(), staged.getSubmittedByUserId());

            staged.setStatus(StagedFeedingStatus.MOVED);
            staged.setMovedFormulationId(created.getId());
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            staged.setValidationErrors(null);
            stagedRepo.save(staged);
            log.info("Staged feeding-formulation {} MOVED -> formulation {}",
                    staged.getId(), created.getId());
        } catch (InsufficientIngredientQuantityException e) {
            // Retriable — stock may be replenished
            onFailure(staged, List.of(e.getMessage()));
        } catch (BusinessException e) {
            // Permanent — bad name, dup, etc.
            markFailed(staged, e.getMessage());
        } catch (Exception e) {
            log.error("Staged feeding-formulation {} threw: {}",
                    staged.getId(), e.getMessage(), e);
            onFailure(staged, List.of("Processing error: " + e.getMessage()));
        }
    }

    private void markFailed(StagedFeedingFormulation staged, String msg) {
        staged.setRetryCount((staged.getRetryCount() == null ? 0 : staged.getRetryCount()) + 1);
        staged.setValidationErrors(truncate(msg));
        staged.setStatus(StagedFeedingStatus.FAILED);
        staged.setProcessedAt(LocalDateTime.now());
        staged.setNextRetryAt(null);
        stagedRepo.save(staged);
    }

    private void onFailure(StagedFeedingFormulation staged, List<String> errors) {
        int attempt = (staged.getRetryCount() == null ? 0 : staged.getRetryCount()) + 1;
        staged.setRetryCount(attempt);
        staged.setValidationErrors(truncate(String.join("; ", errors)));
        if (attempt >= maxAttempts) {
            staged.setStatus(StagedFeedingStatus.FAILED);
            staged.setProcessedAt(LocalDateTime.now());
            staged.setNextRetryAt(null);
            log.error("Staged feeding-formulation {} FAILED after {} attempts: {}",
                    staged.getId(), attempt, errors);
        } else {
            staged.setStatus(StagedFeedingStatus.RETRYING);
            staged.setNextRetryAt(LocalDateTime.now().plusMinutes(retryDelayMinutes));
            log.warn("Staged feeding-formulation {} RETRY {}/{} — {}",
                    staged.getId(), attempt, maxAttempts, errors);
        }
        stagedRepo.save(staged);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= ERROR_FIELD_LIMIT ? s : s.substring(0, ERROR_FIELD_LIMIT);
    }

    private static boolean isDue(StagedFeedingFormulation s, LocalDateTime now) {
        if (s.getStatus() == StagedFeedingStatus.PENDING) return true;
        if (s.getStatus() != StagedFeedingStatus.RETRYING) return false;
        return s.getNextRetryAt() == null || !s.getNextRetryAt().isAfter(now);
    }
}