package com.agriva.poultry.mapper;

import com.agriva.poultry.model.dto.response.PoultryBatchResponse;
import com.agriva.poultry.model.entity.PoultryBatch;
import org.springframework.stereotype.Component;

@Component
public class PoultryBatchMapper {

    public PoultryBatchResponse toResponse(PoultryBatch b, String houseName) {
        return PoultryBatchResponse.builder()
                .id(b.getId())
                .farmId(b.getFarmId())
                .houseId(b.getHouseId())
                .houseName(houseName)
                .species(b.getSpecies())
                .batchCode(b.getBatchCode())
                .initialCount(b.getInitialCount())
                .currentCount(b.getCurrentCount())
                .arrivalDate(b.getArrivalDate())
                .stage(b.getStage())
                .averageWeightG(b.getAverageWeightG())
                .status(b.getStatus())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .build();
    }
}