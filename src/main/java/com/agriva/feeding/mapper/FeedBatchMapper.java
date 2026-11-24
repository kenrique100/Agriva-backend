package com.agriva.feeding.mapper;

import com.agriva.feeding.model.dto.response.FarmStoreResponse;
import com.agriva.feeding.model.dto.response.FeedBatchResponse;
import com.agriva.feeding.model.entity.FarmFeedStore;
import com.agriva.feeding.model.entity.FeedBatch;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class FeedBatchMapper {

    public FeedBatchResponse toResponse(FeedBatch b) {
        return FeedBatchResponse.builder()
                .id(b.getId())
                .farmId(b.getFarmId())
                .formulationId(b.getFormulationId())
                .forecastId(b.getForecastId())
                .formulationName(b.getFormulationName())
                .producedKg(b.getProducedKg())
                .bagSizeKg(b.getBagSizeKg())
                .numberOfBags(b.getNumberOfBags())
                .bulkRemainderKg(b.getBulkRemainderKg())
                .costTotal(b.getCostTotal())
                .productionDate(b.getProductionDate())
                .notes(b.getNotes())
                .createdByUserId(b.getCreatedByUserId())
                .createdAt(b.getCreatedAt())
                .build();
    }

    public FarmStoreResponse toStoreResponse(UUID farmId, FarmFeedStore store, int bagSizeKg) {
        BigDecimal qty = store == null || store.getQuantityOnHandKg() == null
                ? BigDecimal.ZERO
                : store.getQuantityOnHandKg();
        LocalDateTime updatedAt = store == null ? null : store.getUpdatedAt();
        Long version = store == null ? null : store.getVersion();

        BigDecimal bagSize = BigDecimal.valueOf(bagSizeKg);
        int fullBags = qty.divideToIntegralValue(bagSize).intValue();
        BigDecimal remainder = qty.subtract(
                bagSize.multiply(BigDecimal.valueOf(fullBags)));

        return FarmStoreResponse.builder()
                .farmId(farmId)
                .quantityOnHandKg(qty)
                .bagSizeKg(bagSize)
                .fullBags(fullBags)
                .remainderKg(remainder)
                .lastUpdatedAt(updatedAt)
                .version(version)
                .build();
    }
}