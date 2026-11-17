package com.agriva.farming.scheduler;

import com.agriva.farming.model.dto.requests.CropCycleRequest;
import com.agriva.farming.model.entity.Crop;
import com.agriva.farming.model.entity.CropCycle;
import com.agriva.farming.model.entity.Field;
import com.agriva.farming.model.entity.StagedCropCycle;
import com.agriva.farming.model.enums.CropCycleStatus;
import com.agriva.farming.model.enums.StagedCropCycleStatus;
import com.agriva.farming.repository.CropCycleRepository;
import com.agriva.farming.repository.CropRepository;
import com.agriva.farming.repository.FieldRepository;
import com.agriva.farming.repository.StagedCropCycleRepository;
import com.agriva.farming.utils.CropCycleRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class StagedCropCycleValidator {

    private static final int MAX_RETRIES = 3;

    private final StagedCropCycleRepository stagedRepo;
    private final FieldRepository fieldRepo;
    private final CropRepository cropRepo;
    private final CropCycleRepository cycleRepo;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;

    @Scheduled(cron = "0 * * * * *")
    public void process() {
        List<StagedCropCycle> due = stagedRepo.findDueForProcessing(
                LocalDateTime.now(),
                StagedCropCycleStatus.PENDING,
                StagedCropCycleStatus.RETRYING,
                PageRequest.of(0, 100));

        for (StagedCropCycle s : due) {
            UUID stagedId = s.getId();
            try {
                txTemplate.execute(status -> {
                    tryProcessOne(stagedId);
                    return null;
                });
            } catch (Exception ex) {
                // Recording uses a fresh transaction so it survives the rollback above.
                log.warn("Staged crop cycle {} failed: {}", stagedId, ex.getMessage());
                try {
                    txTemplate.execute(status -> {
                        recordFailure(stagedId, ex.getMessage());
                        return null;
                    });
                } catch (Exception recordEx) {
                    log.error("Failed to record failure for staged cycle {}: {}",
                            stagedId, recordEx.getMessage(), recordEx);
                }
            }
        }
    }

    private void tryProcessOne(UUID stagedId) {
        StagedCropCycle s = stagedRepo.findByIdForUpdate(stagedId)
                .filter(this::isProcessableStatus)
                .orElse(null);
        if (s == null) return;

        s.setLastAttemptAt(LocalDateTime.now());

        CropCycleRequest req = readPayload(s);
        Field field = fieldRepo.findByIdAndFarmId(req.getFieldId(), s.getFarmId())
                .orElseThrow(() -> new IllegalStateException("Field not found: " + req.getFieldId()));
        Crop crop = cropRepo.findByIdAndFarmId(req.getCropId(), s.getFarmId())
                .orElseThrow(() -> new IllegalStateException("Crop not found: " + req.getCropId()));

        CropCycle cycle = CropCycle.builder()
                .farmId(s.getFarmId())
                .field(field)
                .crop(crop)
                .plantingDate(req.getPlantingDate())
                .expectedHarvestDate(CropCycleRules.expectedHarvestFor(crop, req.getPlantingDate()))
                .status(CropCycleStatus.PLANNED)
                .notes(req.getNotes())
                .build();

        CropCycle saved = cycleRepo.save(cycle);

        s.setStatus(StagedCropCycleStatus.MOVED);
        s.setMovedCycleId(saved.getId());
        s.setProcessedAt(LocalDateTime.now());
        stagedRepo.save(s);
        log.info("Staged crop cycle {} MOVED -> {}", s.getId(), saved.getId());
    }

    private void recordFailure(UUID stagedId, String message) {
        StagedCropCycle s = stagedRepo.findByIdForUpdate(stagedId).orElse(null);
        if (s == null) return;

        s.setRetryCount(s.getRetryCount() + 1);
        s.setValidationErrors(message);
        if (s.getRetryCount() >= MAX_RETRIES) {
            s.setStatus(StagedCropCycleStatus.FAILED);
            s.setNextRetryAt(null);
        } else {
            s.setStatus(StagedCropCycleStatus.RETRYING);
            s.setNextRetryAt(LocalDateTime.now().plusMinutes(5));
        }
        stagedRepo.save(s);
    }

    private CropCycleRequest readPayload(StagedCropCycle s) {
        try {
            return objectMapper.readValue(s.getPayloadJson(), CropCycleRequest.class);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid payload JSON: " + e.getMessage(), e);
        }
    }

    private boolean isProcessableStatus(StagedCropCycle s) {
        if (s.getStatus() == StagedCropCycleStatus.PENDING) return true;
        return s.getStatus() == StagedCropCycleStatus.RETRYING
                && (s.getNextRetryAt() == null
                || !s.getNextRetryAt().isAfter(LocalDateTime.now()));
    }
}