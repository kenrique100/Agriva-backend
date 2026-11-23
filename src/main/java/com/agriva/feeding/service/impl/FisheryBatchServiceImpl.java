package com.agriva.feeding.service.impl;

import com.agriva.feeding.model.entity.FisheryBatch;
import com.agriva.feeding.repository.FisheryBatchRepository;
import com.agriva.feeding.service.FisheryBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FisheryBatchServiceImpl implements FisheryBatchService {

    private final FisheryBatchRepository repository;

    @Override
    @Transactional
    public FisheryBatch createBatch(UUID farmId, UUID pondId, String species, Long headCount, LocalDate startDate) {
        FisheryBatch batch = FisheryBatch.builder()
                .farmId(farmId)
                .pondId(pondId)
                .species(species)
                .initialHeadCount(headCount)
                .currentHeadCount(headCount)
                .startDate(startDate)
                .status("ACTIVE")
                .build();
        return repository.save(batch);
    }

    @Override
    @Transactional
    public void updateHeadCount(UUID batchId, Long newHeadCount) {
        repository.findById(batchId).ifPresent(batch -> {
            batch.setCurrentHeadCount(newHeadCount);
            if (newHeadCount <= 0) {
                batch.setStatus("HARVESTED");
            }
            repository.save(batch);
        });
    }
}