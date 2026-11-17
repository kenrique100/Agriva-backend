package com.agriva.poultry.mapper;

import com.agriva.poultry.model.dto.response.EggRecordResponse;
import com.agriva.poultry.model.dto.response.MortalityRecordResponse;
import com.agriva.poultry.model.dto.response.WeightSampleResponse;
import com.agriva.poultry.model.entity.PoultryEggRecord;
import com.agriva.poultry.model.entity.PoultryMortalityRecord;
import com.agriva.poultry.model.entity.PoultryWeightSample;
import org.springframework.stereotype.Component;

@Component
public class PoultryMetricsMapper {

    public MortalityRecordResponse toResponse(PoultryMortalityRecord m) {
        return MortalityRecordResponse.builder()
                .id(m.getId())
                .farmId(m.getFarmId())
                .batchId(m.getBatchId())
                .quantity(m.getQuantity())
                .recordedAt(m.getRecordedAt())
                .suspectedCause(m.getSuspectedCause())
                .recordedByUserId(m.getRecordedByUserId())
                .createdAt(m.getCreatedAt())
                .build();
    }

    public WeightSampleResponse toResponse(PoultryWeightSample s) {
        return WeightSampleResponse.builder()
                .id(s.getId())
                .farmId(s.getFarmId())
                .batchId(s.getBatchId())
                .averageWeightG(s.getAverageWeightG())
                .sampleSize(s.getSampleSize())
                .sampledAt(s.getSampledAt())
                .recordedByUserId(s.getRecordedByUserId())
                .createdAt(s.getCreatedAt())
                .build();
    }

    public EggRecordResponse toResponse(PoultryEggRecord e) {
        return EggRecordResponse.builder()
                .id(e.getId())
                .farmId(e.getFarmId())
                .batchId(e.getBatchId())
                .eggsCollected(e.getEggsCollected())
                .brokenEggs(e.getBrokenEggs())
                .recordedAt(e.getRecordedAt())
                .recordedByUserId(e.getRecordedByUserId())
                .createdAt(e.getCreatedAt())
                .build();
    }
}