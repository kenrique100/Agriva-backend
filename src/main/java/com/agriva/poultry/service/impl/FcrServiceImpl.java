package com.agriva.poultry.service.impl;

import com.agriva.feeding.port.ConsumerType;
import com.agriva.feeding.port.FeedingHistoryPort;
import com.agriva.poultry.model.dto.response.BatchFcrResponse;
import com.agriva.poultry.model.entity.PoultryBatch;
import com.agriva.poultry.model.enums.PoultryBatchStatus;
import com.agriva.poultry.repository.PoultryBatchRepository;
import com.agriva.poultry.service.FcrService;
import com.agriva.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcrServiceImpl implements FcrService {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final int SCALE = 4;

    private final PoultryBatchRepository batchRepo;
    private final FeedingHistoryPort feedingHistoryPort;

    @Override
    @Transactional(readOnly = true)
    public BatchFcrResponse compute(UUID batchId, UUID farmId) {
        PoultryBatch batch = batchRepo.findByIdAndFarmId(batchId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Poultry batch not found with ID: " + batchId));

        LocalDate from = batch.getArrivalDate();
        LocalDate to = LocalDate.now();

        // Only one ACTIVE batch per house keeps feed attribution unambiguous:
        // FeedingEvent is recorded at the house level, not the batch level.
        long activeBatches = batchRepo.findActiveByHouseId(batch.getHouseId()).size();

        BigDecimal feedConsumed = feedingHistoryPort.totalFeedConsumedKg(
                farmId, ConsumerType.POULTRY, batch.getHouseId(), from, to);

        BigDecimal initialBiomass = toBiomassKg(
                batch.getInitialAvgWeightG(), batch.getInitialCount());
        BigDecimal currentBiomass = toBiomassKg(
                batch.getAverageWeightG(), batch.getCurrentCount());

        BatchFcrResponse.BatchFcrResponseBuilder b = BatchFcrResponse.builder()
                .batchId(batch.getId())
                .houseId(batch.getHouseId())
                .from(from)
                .to(to)
                .feedConsumedKg(feedConsumed)
                .initialBiomassKg(initialBiomass)
                .currentBiomassKg(currentBiomass)
                .initialCount(batch.getInitialCount())
                .currentCount(batch.getCurrentCount())
                .initialAvgWeightG(batch.getInitialAvgWeightG())
                .currentAvgWeightG(batch.getAverageWeightG());

        if (batch.getStatus() != PoultryBatchStatus.ACTIVE) {
            return b.note("batch is not ACTIVE — FCR is only computed for live flocks").build();
        }
        if (activeBatches > 1) {
            return b.note("multiple active batches in house — FCR per batch is ambiguous").build();
        }
        if (initialBiomass == null || currentBiomass == null) {
            return b.note("weight data missing — record a weight sample to enable FCR").build();
        }

        BigDecimal weightGain = currentBiomass.subtract(initialBiomass);
        b.weightGainKg(weightGain);
        if (weightGain.signum() <= 0) {
            return b.note("weight gain is zero or negative — check your weight samples").build();
        }
        if (feedConsumed.signum() == 0) {
            return b.note("no feed has been delivered to the house since batch arrival").build();
        }

        BigDecimal fcr = feedConsumed.divide(weightGain, SCALE, RoundingMode.HALF_UP);
        b.fcr(fcr);
        log.info("FCR computed for batch {} (house {}): feed={} kg, gain={} kg, FCR={}",
                batch.getId(), batch.getHouseId(), feedConsumed, weightGain, fcr);
        return b.build();
    }

    private BigDecimal toBiomassKg(BigDecimal avgWeightG, Long count) {
        if (avgWeightG == null || count == null || count <= 0) return null;
        return avgWeightG.multiply(BigDecimal.valueOf(count))
                .divide(THOUSAND, SCALE, RoundingMode.HALF_UP);
    }
}