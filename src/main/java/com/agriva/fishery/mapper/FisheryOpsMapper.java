package com.agriva.fishery.mapper;

import com.agriva.fishery.model.dto.response.GrowthSampleResponse;
import com.agriva.fishery.model.dto.response.MortalityResponse;
import com.agriva.fishery.model.dto.response.TreatmentResponse;
import com.agriva.fishery.model.entity.GrowthSample;
import com.agriva.fishery.model.entity.MortalityRecord;
import com.agriva.fishery.model.entity.Treatment;
import org.springframework.stereotype.Component;

@Component
public class FisheryOpsMapper {

    public TreatmentResponse toResponse(Treatment t) {
        return TreatmentResponse.builder()
                .id(t.getId())
                .farmId(t.getFarmId())
                .pondId(t.getPondId())
                .medicationName(t.getMedicationName())
                .dosage(t.getDosage())
                .dosageUnit(t.getDosageUnit())
                .administeredAt(t.getAdministeredAt())
                .withdrawalDays(t.getWithdrawalDays())
                .administeredByUserId(t.getAdministeredByUserId())
                .notes(t.getNotes())
                .createdAt(t.getCreatedAt())
                .build();
    }

    public MortalityResponse toResponse(MortalityRecord m) {
        return MortalityResponse.builder()
                .id(m.getId())
                .farmId(m.getFarmId())
                .pondId(m.getPondId())
                .species(m.getSpecies())
                .quantity(m.getQuantity())
                .recordedAt(m.getRecordedAt())
                .suspectedCause(m.getSuspectedCause())
                .recordedByUserId(m.getRecordedByUserId())
                .createdAt(m.getCreatedAt())
                .build();
    }

    public GrowthSampleResponse toResponse(GrowthSample g) {
        return GrowthSampleResponse.builder()
                .id(g.getId())
                .farmId(g.getFarmId())
                .pondId(g.getPondId())
                .species(g.getSpecies())
                .averageWeightG(g.getAverageWeightG())
                .sampleSize(g.getSampleSize())
                .sampledAt(g.getSampledAt())
                .recordedByUserId(g.getRecordedByUserId())
                .createdAt(g.getCreatedAt())
                .build();
    }
}