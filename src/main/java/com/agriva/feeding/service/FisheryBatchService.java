package com.agriva.feeding.service;

import com.agriva.feeding.model.entity.FisheryBatch;
import java.util.UUID;

public interface FisheryBatchService {
    FisheryBatch createBatch(UUID farmId, UUID pondId, String species, Long headCount, java.time.LocalDate startDate);
    void updateHeadCount(UUID batchId, Long newHeadCount);
}