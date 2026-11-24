package com.agriva.feeding.calculator;

import com.agriva.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Primary
@Slf4j
public class FishDemandForecaster implements FeedDemandForecaster {

    private static final String VERSION = "agriva-aquaculture-v2";
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final int SCALE = 4;

    private record StageDef(String name,
                            BigDecimal startG,
                            BigDecimal endG,
                            BigDecimal dailyFeedPctBw,
                            int feedingsPerDay,
                            BigDecimal dwgGPerDay) {}

    private static final List<StageDef> CATFISH_STAGES = List.of(
            new StageDef("Juvenile",
                    new BigDecimal("10"), new BigDecimal("50"),
                    new BigDecimal("4.48"), 4, new BigDecimal("0.769")),
            new StageDef("Sub-adult",
                    new BigDecimal("50"), new BigDecimal("200"),
                    new BigDecimal("2.70"), 3, new BigDecimal("3.333")),
            new StageDef("Adult",
                    new BigDecimal("200"), new BigDecimal("2000"),
                    new BigDecimal("1.80"), 2, new BigDecimal("24.00"))
    );

    private static final List<StageDef> TILAPIA_STAGES = List.of(
            new StageDef("FRY",
                    new BigDecimal("0.01"), new BigDecimal("5"),
                    new BigDecimal("8.0"), 4, new BigDecimal("0.10")),
            new StageDef("FINGERLING",
                    new BigDecimal("5"), new BigDecimal("50"),
                    new BigDecimal("5.0"), 3, new BigDecimal("0.50")),
            new StageDef("JUVENILE",
                    new BigDecimal("50"), new BigDecimal("200"),
                    new BigDecimal("3.5"), 3, new BigDecimal("1.50")),
            new StageDef("GROWER",
                    new BigDecimal("200"), new BigDecimal("500"),
                    new BigDecimal("2.5"), 2, new BigDecimal("3.00")),
            new StageDef("FINISHER",
                    new BigDecimal("500"), new BigDecimal("2000"),
                    new BigDecimal("2.0"), 2, new BigDecimal("5.00"))
    );

    private static final Map<String, List<StageDef>> PROFILES = Map.of(
            "CATFISH", CATFISH_STAGES,
            "TILAPIA", TILAPIA_STAGES
    );

    @Override
    public DemandForecastResult calculate(DemandForecastInput input) {
        if (input == null) {
            throw new BusinessException("Forecast input is required");
        }

        String speciesKey = input.species() == null
                ? ""
                : input.species().trim().toUpperCase(Locale.ROOT);
        List<StageDef> stagesProfile = null;

        // Match species flexibly (e.g., "African Catfish", "Nile Tilapia")
        for (String key : PROFILES.keySet()) {
            if (speciesKey.contains(key)) {
                stagesProfile = PROFILES.get(key);
                break;
            }
        }

        if (stagesProfile == null) {
            throw new BusinessException("Species '" + input.species()
                    + "' is not supported. Supported: CATFISH, TILAPIA");
        }

        if (input.numberOfHead() <= 0) {
            throw new BusinessException("numberOfHead must be positive");
        }

        if (input.initialWeightKg() == null || input.targetWeightKg() == null) {
            throw new BusinessException("initialWeightKg and targetWeightKg are required");
        }

        BigDecimal initialG = input.initialWeightKg().multiply(THOUSAND);
        BigDecimal targetG = input.targetWeightKg().multiply(THOUSAND);
        if (initialG.compareTo(targetG) >= 0) {
            throw new BusinessException("Initial weight must be less than target weight");
        }

        List<GrowthStageResult> stages = new ArrayList<>();
        BigDecimal totalFeedKg = BigDecimal.ZERO;
        int totalDays = 0;

        for (StageDef def : stagesProfile) {
            // Calculate the overlap of the requested range with this stage's bounds
            BigDecimal overlapStart = initialG.max(def.startG);
            BigDecimal overlapEnd = targetG.min(def.endG);
            if (overlapStart.compareTo(overlapEnd) >= 0) continue;

            BigDecimal weightGainG = overlapEnd.subtract(overlapStart);
            int daysInStage = weightGainG
                    .divide(def.dwgGPerDay, 0, RoundingMode.CEILING)
                    .intValue();
            if (daysInStage <= 0) continue;

            BigDecimal avgWeightG = overlapStart.add(overlapEnd)
                    .divide(TWO, SCALE, RoundingMode.HALF_UP);
            BigDecimal avgWeightKg = avgWeightG.divide(THOUSAND, SCALE, RoundingMode.HALF_UP);

            // PER-FISH daily feed for this stage
            BigDecimal dailyFeedPerHeadKg = avgWeightKg
                    .multiply(def.dailyFeedPctBw)
                    .divide(HUNDRED, SCALE, RoundingMode.HALF_UP);

            // PER-FISH feed consumed over the stage
            BigDecimal feedConsumedPerHeadKg = dailyFeedPerHeadKg
                    .multiply(BigDecimal.valueOf(daysInStage))
                    .setScale(SCALE, RoundingMode.HALF_UP);

            // Stage result uses PER-FISH metrics (matches entity contract)
            stages.add(new GrowthStageResult(
                    def.name,
                    overlapStart.divide(THOUSAND, SCALE, RoundingMode.HALF_UP),
                    overlapEnd.divide(THOUSAND, SCALE, RoundingMode.HALF_UP),
                    daysInStage,
                    feedConsumedPerHeadKg,
                    dailyFeedPerHeadKg,
                    def.feedingsPerDay
            ));

            // Accumulate POPULATION total
            BigDecimal stageTotalFeedKg = feedConsumedPerHeadKg
                    .multiply(BigDecimal.valueOf(input.numberOfHead()));
            totalFeedKg = totalFeedKg.add(stageTotalFeedKg);
            totalDays += daysInStage;
        }

        if (stages.isEmpty() || totalDays <= 0) {
            throw new BusinessException("Could not compute forecast for the given weight range");
        }

        // Average daily feed for the ENTIRE population
        BigDecimal avgDaily = totalFeedKg.divide(
                BigDecimal.valueOf(totalDays), SCALE, RoundingMode.HALF_UP);

        log.info("{} forecast computed: head={}, initial={}kg, target={}kg, "
                        + "stages={}, totalDays={}, totalFeed={}kg",
                speciesKey, input.numberOfHead(), input.initialWeightKg(), input.targetWeightKg(),
                stages.size(), totalDays, totalFeedKg.setScale(2, RoundingMode.HALF_UP));

        return new DemandForecastResult(
                stages,
                totalDays,
                totalFeedKg,
                avgDaily,
                VERSION + "-" + speciesKey
        );
    }

    @Override
    public String algorithmVersion() {
        return VERSION;
    }
}