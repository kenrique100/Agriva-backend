package com.agriva.poultry.mapper;

import com.agriva.poultry.model.dto.response.PoultryHouseResponse;
import com.agriva.poultry.model.entity.PoultryBatch;
import com.agriva.poultry.model.entity.PoultryHouse;
import com.agriva.poultry.model.enums.PoultryBatchStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PoultryHouseMapper {

    public PoultryHouseResponse toResponse(PoultryHouse house) {
        return toResponse(house, null);
    }

    public PoultryHouseResponse toResponse(PoultryHouse house,
                                           PoultryHouseResponse.BatchSummary summary) {
        return PoultryHouseResponse.builder()
                .id(house.getId())
                .farmId(house.getFarmId())
                .name(house.getName())
                .location(house.getLocation())
                .capacity(house.getCapacity())
                .active(house.isActive())
                .createdAt(house.getCreatedAt())
                .updatedAt(house.getUpdatedAt())
                .batches(summary)
                .build();
    }

    /**
     * Aggregates a house's batches into one summary. Only ACTIVE batches
     * contribute to headcount, species list, and weighted average weight.
     * Species are ordered by current headcount desc then name asc so the
     * dominant species appears first (matches Fishery's PondMapper).
     */
    public PoultryHouseResponse.BatchSummary toBatchSummary(List<PoultryBatch> batches) {
        List<PoultryBatch> active = batches.stream()
                .filter(b -> b.getStatus() == PoultryBatchStatus.ACTIVE)
                .toList();

        if (active.isEmpty()) {
            return PoultryHouseResponse.BatchSummary.builder()
                    .totalCurrentCount(0L)
                    .totalInitialCount(0L)
                    .batchCount(0)
                    .species(List.of())
                    .build();
        }

        long totalCurrent = active.stream()
                .mapToLong(b -> b.getCurrentCount() == null ? 0L : b.getCurrentCount())
                .sum();
        long totalInitial = active.stream()
                .mapToLong(b -> b.getInitialCount() == null ? 0L : b.getInitialCount())
                .sum();

        Map<String, Long> bySpecies = active.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getSpecies().name(),
                        Collectors.summingLong(b -> b.getCurrentCount() == null ? 0L : b.getCurrentCount())));

        List<String> species = bySpecies.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();

        BigDecimal weightedSum = BigDecimal.ZERO;
        long weightedQty = 0L;
        for (PoultryBatch b : active) {
            if (b.getAverageWeightG() != null && b.getCurrentCount() != null
                    && b.getCurrentCount() > 0) {
                weightedSum = weightedSum.add(
                        b.getAverageWeightG().multiply(BigDecimal.valueOf(b.getCurrentCount())));
                weightedQty += b.getCurrentCount();
            }
        }
        BigDecimal avgWeight = weightedQty > 0
                ? weightedSum.divide(BigDecimal.valueOf(weightedQty), 2, RoundingMode.HALF_UP)
                : null;

        String dominantStage = active.stream()
                .filter(b -> b.getStage() != null && b.getCurrentCount() != null)
                .collect(Collectors.groupingBy(
                        PoultryBatch::getStage,
                        Collectors.summingLong(PoultryBatch::getCurrentCount)))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        return PoultryHouseResponse.BatchSummary.builder()
                .totalCurrentCount(totalCurrent)
                .totalInitialCount(totalInitial)
                .batchCount(active.size())
                .species(species)
                .averageWeightG(avgWeight)
                .dominantStage(dominantStage)
                .build();
    }
}