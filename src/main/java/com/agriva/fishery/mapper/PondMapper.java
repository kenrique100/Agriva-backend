package com.agriva.fishery.mapper;

import com.agriva.fishery.model.dto.response.PondResponse;
import com.agriva.fishery.model.dto.response.PondStockResponse;
import com.agriva.fishery.model.entity.Pond;
import com.agriva.fishery.model.entity.PondStock;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PondMapper {

    /** List view — no stock summary (avoids N+1). */
    public PondResponse toResponse(Pond pond) {
        return toResponse(pond, null);
    }

    /** Detail view — with stock summary. */
    public PondResponse toResponse(Pond pond, PondResponse.StockSummary stock) {
        return PondResponse.builder()
                .id(pond.getId())
                .farmId(pond.getFarmId())
                .name(pond.getName())
                .location(pond.getLocation())
                .surfaceAreaM2(pond.getSurfaceAreaM2())
                .depthM(pond.getDepthM())
                .active(pond.isActive())
                .createdAt(pond.getCreatedAt())
                .updatedAt(pond.getUpdatedAt())
                .stock(stock)
                .build();
    }

    public PondStockResponse toStockResponse(PondStock stock) {
        return PondStockResponse.builder()
                .id(stock.getId())
                .pondId(stock.getPondId())
                .species(stock.getSpecies())
                .quantity(stock.getQuantity())
                .averageWeightG(stock.getAverageWeightG())
                .stage(stock.getStage())
                .stockedAt(stock.getStockedAt())
                .build();
    }

    /**
     * Aggregates a pond's per-species stock rows into one summary:
     * total headcount, species list ordered by headcount desc then name asc
     * (so the dominant species appears first), weighted average weight,
     * and the stage held by the largest group.
     */
    public PondResponse.StockSummary toStockSummary(List<PondStock> stocks) {
        if (stocks == null || stocks.isEmpty()) return null;

        long totalQuantity = stocks.stream()
                .mapToLong(s -> s.getQuantity() == null ? 0L : s.getQuantity())
                .sum();

        Map<String, Long> bySpecies = stocks.stream()
                .collect(Collectors.groupingBy(
                        PondStock::getSpecies,
                        Collectors.summingLong(s -> s.getQuantity() == null ? 0L : s.getQuantity())));

        List<String> species = bySpecies.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();

        // Weighted average: sum(avgG * qty) / sum(qty), only over rows that have avgG.
        BigDecimal weightedSum = BigDecimal.ZERO;
        long weightedQty = 0L;
        for (PondStock s : stocks) {
            if (s.getAverageWeightG() != null && s.getQuantity() != null && s.getQuantity() > 0) {
                weightedSum = weightedSum.add(
                        s.getAverageWeightG().multiply(BigDecimal.valueOf(s.getQuantity())));
                weightedQty += s.getQuantity();
            }
        }
        BigDecimal averageWeightG = weightedQty > 0
                ? weightedSum.divide(BigDecimal.valueOf(weightedQty), 2, RoundingMode.HALF_UP)
                : null;

        String dominantStage = stocks.stream()
                .filter(s -> s.getStage() != null && s.getQuantity() != null)
                .collect(Collectors.groupingBy(
                        PondStock::getStage,
                        Collectors.summingLong(PondStock::getQuantity)))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        return PondResponse.StockSummary.builder()
                .totalQuantity(totalQuantity)
                .species(species)
                .averageWeightG(averageWeightG)
                .dominantStage(dominantStage)
                .build();
    }
}