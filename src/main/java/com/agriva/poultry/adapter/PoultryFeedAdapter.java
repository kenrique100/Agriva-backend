package com.agriva.poultry.adapter;

import com.agriva.feeding.port.ConsumerSnapshot;
import com.agriva.feeding.port.ConsumerType;
import com.agriva.feeding.port.FeedConsumerPort;
import com.agriva.poultry.model.entity.PoultryBatch;
import com.agriva.poultry.model.entity.PoultryHouse;
import com.agriva.poultry.repository.PoultryBatchRepository;
import com.agriva.poultry.repository.PoultryHouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bridges Poultry into the Feeding hub's consumption flow.
 * <p>
 * Headcount aggregates all ACTIVE batches in the house. Display name is
 * {@code "<House> – <Species1>, <Species2>"} with species ordered by current
 * headcount descending, mirroring {@code FisheryFeedAdapter}. Stage is the
 * dominant stage across active batches (weighted by current headcount).
 * <p>
 * {@code resolveConsumerIdByDisplayName} accepts either the full formatted
 * string or the bare house name, so the Excel template works with minimal
 * typing. When the poultry feature flag is disabled, this bean does not exist
 * and feeding returns {@code 422 Consumer type POULTRY is not registered}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "agriva.poultry.enabled", havingValue = "true", matchIfMissing = true)
public class PoultryFeedAdapter implements FeedConsumerPort {

    private static final String EN_DASH = " – ";
    private static final String HYPHEN = " - ";

    private final PoultryHouseRepository houseRepository;
    private final PoultryBatchRepository batchRepository;

    @Override
    public ConsumerType consumerType() {
        return ConsumerType.POULTRY;
    }

    @Override
    public Optional<ConsumerSnapshot> snapshot(UUID farmId, UUID houseId) {
        Optional<PoultryHouse> houseOpt = houseRepository
                .findByIdAndFarmIdLive(houseId, farmId)
                .filter(PoultryHouse::isActive);
        if (houseOpt.isEmpty()) return Optional.empty();

        PoultryHouse house = houseOpt.get();
        List<PoultryBatch> active = batchRepository.findActiveByHouseId(houseId);

        long headcount = active.stream()
                .mapToLong(b -> b.getCurrentCount() == null ? 0L : b.getCurrentCount())
                .sum();

        return Optional.of(new ConsumerSnapshot(
                houseId,
                buildDisplayName(house, active),
                headcount,
                dominantStage(active)));
    }

    @Override
    public Optional<UUID> resolveConsumerIdByDisplayName(UUID farmId, String displayName) {
        if (displayName == null || displayName.isBlank()) return Optional.empty();
        String trimmed = displayName.trim();

        Optional<PoultryHouse> exact = houseRepository.findByFarmIdAndNameLive(farmId, trimmed);
        if (exact.isPresent()) return exact.map(PoultryHouse::getId);

        for (String sep : new String[]{EN_DASH, HYPHEN}) {
            int idx = trimmed.indexOf(sep);
            if (idx > 0) {
                String nameOnly = trimmed.substring(0, idx).trim();
                Optional<PoultryHouse> byName =
                        houseRepository.findByFarmIdAndNameLive(farmId, nameOnly);
                if (byName.isPresent()) return byName.map(PoultryHouse::getId);
            }
        }
        log.debug("PoultryFeedAdapter could not resolve displayName='{}' for farm {}",
                displayName, farmId);
        return Optional.empty();
    }

    private String buildDisplayName(PoultryHouse house, List<PoultryBatch> active) {
        String speciesList = active.stream()
                .collect(Collectors.groupingBy(
                        b -> b.getSpecies().name(),
                        Collectors.summingLong(b -> b.getCurrentCount() == null ? 0L : b.getCurrentCount())))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .collect(Collectors.joining(", "));

        return speciesList.isEmpty()
                ? house.getName()
                : house.getName() + EN_DASH + speciesList;
    }

    private String dominantStage(List<PoultryBatch> active) {
        return active.stream()
                .filter(b -> b.getStage() != null && b.getCurrentCount() != null)
                .collect(Collectors.groupingBy(
                        PoultryBatch::getStage,
                        Collectors.summingLong(PoultryBatch::getCurrentCount)))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}