package com.agriva.feeding.calculator;

import com.agriva.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component("tilapiaDemandForecaster")
@Slf4j
public class TilapiaDemandForecaster implements FeedDemandForecaster {

    private static final String VERSION = "agriva-tilapia-v1";
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

    private static final List<StageDef> STAGES = List.of(
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

    @Override
    public DemandForecastResult calculate(DemandForecastInput input) {
        if (input == null) {
            throw new BusinessException("Forecast input is required");
        }
        String key = SpeciesKey.of(input.species());
        if (!SpeciesKey.TILAPIA.equals(key)) {
            throw new BusinessException("Species '" + input.species()
                    + "' is not supported by this algorithm. Currently supported: TILAPIA");
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

        for (StageDef def : STAGES) {
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

            BigDecimal dailyFeedPerHeadKg = avgWeightKg
                    .multiply(def.dailyFeedPctBw)
                    .divide(HUNDRED, SCALE, RoundingMode.HALF_UP);

            BigDecimal feedConsumedPerHeadKg = dailyFeedPerHeadKg
                    .multiply(BigDecimal.valueOf(daysInStage))
                    .setScale(SCALE, RoundingMode.HALF_UP);

            stages.add(new GrowthStageResult(
                    def.name,
                    overlapStart.divide(THOUSAND, SCALE, RoundingMode.HALF_UP),
                    overlapEnd.divide(THOUSAND, SCALE, RoundingMode.HALF_UP),
                    daysInStage,
                    feedConsumedPerHeadKg,
                    dailyFeedPerHeadKg,
                    def.feedingsPerDay
            ));

            BigDecimal stageTotalFeedKg = feedConsumedPerHeadKg
                    .multiply(BigDecimal.valueOf(input.numberOfHead()));
            totalFeedKg = totalFeedKg.add(stageTotalFeedKg);
            totalDays += daysInStage;
        }

        if (stages.isEmpty() || totalDays <= 0) {
            throw new BusinessException(
                    "Could not compute forecast for the given weight range");
        }

        BigDecimal avgDaily = totalFeedKg.divide(
                BigDecimal.valueOf(totalDays), SCALE, RoundingMode.HALF_UP);

        log.info("Tilapia forecast computed: head={}, initial={}kg, target={}kg, "
                        + "stages={}, totalDays={}, totalFeed={}kg",
                input.numberOfHead(), input.initialWeightKg(), input.targetWeightKg(),
                stages.size(), totalDays, totalFeedKg);

        return new DemandForecastResult(stages, totalDays, totalFeedKg, avgDaily, VERSION);
    }

    @Override
    public String algorithmVersion() {
        return VERSION;
    }
}