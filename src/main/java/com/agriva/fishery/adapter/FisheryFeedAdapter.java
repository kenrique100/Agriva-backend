package com.agriva.fishery.adapter;

import com.agriva.feeding.port.ConsumerSnapshot;
import com.agriva.feeding.port.ConsumerType;
import com.agriva.feeding.port.FeedConsumerPort;
import com.agriva.fishery.model.entity.GrowthSample;
import com.agriva.fishery.model.entity.Pond;
import com.agriva.fishery.model.entity.PondStock;
import com.agriva.fishery.repository.GrowthSampleRepository;
import com.agriva.fishery.repository.PondRepository;
import com.agriva.fishery.repository.PondStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bridges Fishery into the Feeding hub's consumption flow.
 * <p>
 * enrichment: when a growth sample exists for the pond, {@code stage}
 * is derived from the latest sample's average weight. Otherwise, the stage
 * comes from the {@code PondStock.stage} field set at stocking time.
 * The port signature is unchanged.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FisheryFeedAdapter implements FeedConsumerPort {

    private static final String EN_DASH = " – ";
    private static final String HYPHEN = " - ";

    // Stage thresholds — deliberately conservative. Advisory only; not
    // enforced anywhere else in the codebase.
    private static final BigDecimal FINGERLING_MAX_G = new BigDecimal("50");
    private static final BigDecimal JUVENILE_MAX_G   = new BigDecimal("200");
    private static final BigDecimal GROWER_MAX_G     = new BigDecimal("500");

    private final PondRepository pondRepository;
    private final PondStockRepository pondStockRepository;
    private final GrowthSampleRepository growthSampleRepository;

    @Override
    public ConsumerType consumerType() {
        return ConsumerType.FISHERY;
    }

    @Override
    public Optional<ConsumerSnapshot> snapshot(UUID farmId, UUID pondId) {
        Optional<Pond> pondOpt = pondRepository.findByIdAndFarmIdLive(pondId, farmId)
                .filter(Pond::isActive);
        if (pondOpt.isEmpty()) return Optional.empty();

        Pond pond = pondOpt.get();
        List<PondStock> stocks = pondStockRepository.findByPondId(pondId);

        long headcount = stocks.stream()
                .mapToLong(s -> s.getQuantity() == null ? 0L : s.getQuantity())
                .sum();

        String displayName = buildDisplayName(pond, stocks);
        String stage = deriveStage(pondId, stocks);

        return Optional.of(new ConsumerSnapshot(pondId, displayName, headcount, stage));
    }

    @Override
    public Optional<UUID> resolveConsumerIdByDisplayName(UUID farmId, String displayName) {
        if (displayName == null || displayName.isBlank()) return Optional.empty();
        String trimmed = displayName.trim();

        Optional<Pond> exact = pondRepository.findByFarmIdAndNameLive(farmId, trimmed);
        if (exact.isPresent()) return exact.map(Pond::getId);

        for (String sep : new String[]{EN_DASH, HYPHEN}) {
            int idx = trimmed.indexOf(sep);
            if (idx > 0) {
                String nameOnly = trimmed.substring(0, idx).trim();
                Optional<Pond> byName = pondRepository.findByFarmIdAndNameLive(farmId, nameOnly);
                if (byName.isPresent()) return byName.map(Pond::getId);
            }
        }
        log.debug("FisheryFeedAdapter could not resolve displayName='{}' for farm {}",
                displayName, farmId);
        return Optional.empty();
    }

    /**
     * DR-7 enrichment: derive stage from the most recent growth sample if one
     * exists. Otherwise, fall back to the dominant {@code PondStock.stage} from
     * DR-1 behavior. A missing sample never breaks the adapter.
     */
    private String deriveStage(UUID pondId, List<PondStock> stocks) {
        Optional<GrowthSample> latest = growthSampleRepository
                .findTopByPondIdOrderBySampledAtDesc(pondId);

        if (latest.isPresent()) {
            BigDecimal avgG = latest.get().getAverageWeightG();
            if (avgG != null) {
                if (avgG.compareTo(FINGERLING_MAX_G) < 0) return "FINGERLING";
                if (avgG.compareTo(JUVENILE_MAX_G)   < 0) return "JUVENILE";
                if (avgG.compareTo(GROWER_MAX_G)     < 0) return "GROWER";
                return "ADULT";
            }
        }

        return dominantStockStage(stocks);
    }

    private String dominantStockStage(List<PondStock> stocks) {
        return stocks.stream()
                .filter(s -> s.getStage() != null && s.getQuantity() != null)
                .collect(Collectors.groupingBy(
                        PondStock::getStage,
                        Collectors.summingLong(PondStock::getQuantity)))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String buildDisplayName(Pond pond, List<PondStock> stocks) {
        String speciesList = stocks.stream()
                .collect(Collectors.groupingBy(
                        PondStock::getSpecies,
                        Collectors.summingLong(s -> s.getQuantity() == null ? 0L : s.getQuantity())))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(", "));

        return speciesList.isEmpty()
                ? pond.getName()
                : pond.getName() + EN_DASH + speciesList;
    }
}