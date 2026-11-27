package com.agriva.feeding.service;

import com.agriva.feeding.model.entity.FisheryBatch;

import java.time.LocalDate;
import java.util.UUID;

public interface FisheryBatchService {

    FisheryBatch createBatch(UUID farmId, UUID pondId, String species,
                             Long headCount, LocalDate startDate);

    void updateHeadCount(UUID farmId, UUID batchId, Long newHeadCount);
}