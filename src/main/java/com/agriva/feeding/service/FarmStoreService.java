package com.agriva.feeding.service;

import com.agriva.feeding.model.dto.response.FeedStoreAdjustmentResponse;
import com.agriva.feeding.model.entity.FarmFeedStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface FarmStoreService {

    FarmFeedStore getOrCreateForUpdate(UUID farmId);

    FarmFeedStore get(UUID farmId);

    FarmFeedStore save(FarmFeedStore store);

    /** Absolute set. Records an {@code ABSOLUTE_SET} adjustment. */
    FarmFeedStore setAbsolute(UUID farmId, BigDecimal newQuantityKg,
                              String reason, Long expectedVersion, UUID userId);

    /** Signed delta. Records a {@code DELTA} adjustment. Result floored at zero. */
    FarmFeedStore applyDelta(UUID farmId, BigDecimal deltaKg, String reason, UUID userId);

    /** Paginated history of every store adjustment recorded for this farm. */
    Page<FeedStoreAdjustmentResponse> listAdjustments(UUID farmId, Pageable pageable);
}