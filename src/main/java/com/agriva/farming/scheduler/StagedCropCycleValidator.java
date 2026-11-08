package com.agriva.farming.scheduler;

import com.agriva.farming.model.entity.Crop;
import com.agriva.farming.model.entity.CropCycle;
import com.agriva.farming.model.entity.Field;
import com.agriva.farming.model.entity.StagedCropCycle;
import com.agriva.farming.model.dto.requests.CropCycleRequest;
import com.agriva.farming.model.enums.CropCycleStatus;
import com.agriva.farming.model.enums.StagedCropCycleStatus;
import com.agriva.farming.repository.CropCycleRepository;
import com.agriva.farming.repository.CropRepository;
import com.agriva.farming.repository.FieldRepository;
import com.agriva.farming.repository.StagedCropCycleRepository;
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
            txTemplate.execute(status -> {
                processOne(stagedId);
                return null;
            });
        }
    }

    private boolean isProcessableStatus(StagedCropCycle stagedCropCycle) {
        if (stagedCropCycle.getStatus() == StagedCropCycleStatus.PENDING) return true;
        return stagedCropCycle.getStatus() == StagedCropCycleStatus.RETRYING
                && (stagedCropCycle.getNextRetryAt() == null || !stagedCropCycle.getNextRetryAt().isAfter(LocalDateTime.now()));
    }

    private void processOne(UUID stagedId) {
        StagedCropCycle s = stagedRepo.findByIdForUpdate(stagedId)
                .filter(this::isProcessableStatus)
                .orElse(null);
        if (s == null) return;

        s.setLastAttemptAt(LocalDateTime.now());
        try {
            CropCycleRequest req = objectMapper.readValue(s.getPayloadJson(), CropCycleRequest.class);

            Field field = fieldRepo.findByIdAndFarmId(req.getFieldId(), s.getFarmId())
                    .orElseThrow(() -> new Exception("Field not found"));
            Crop crop = cropRepo.findByIdAndFarmId(req.getCropId(), s.getFarmId())
                    .orElseThrow(() -> new Exception("Crop not found"));

            CropCycle cycle = CropCycle.builder()
                    .farmId(s.getFarmId()).field(field).crop(crop)
                    .plantingDate(req.getPlantingDate())
                    .expectedHarvestDate(req.getPlantingDate().plusDays(crop.getExpectedDaysToHarvest() != null ? crop.getExpectedDaysToHarvest() : 90))
                    .status(CropCycleStatus.PLANNED).notes(req.getNotes())
                    .build();
            CropCycle saved = cycleRepo.save(cycle);

            s.setStatus(StagedCropCycleStatus.MOVED);
            s.setMovedCycleId(saved.getId());
            s.setProcessedAt(LocalDateTime.now());
            stagedRepo.save(s);
            log.info("Staged crop cycle {} MOVED -> {}", s.getId(), saved.getId());
        } catch (Exception e) {
            s.setRetryCount(s.getRetryCount() + 1);
            s.setValidationErrors(e.getMessage());
            if (s.getRetryCount() >= 3) s.setStatus(StagedCropCycleStatus.FAILED);
            else { s.setStatus(StagedCropCycleStatus.RETRYING); s.setNextRetryAt(LocalDateTime.now().plusMinutes(5)); }
            stagedRepo.save(s);
        }
    }
}