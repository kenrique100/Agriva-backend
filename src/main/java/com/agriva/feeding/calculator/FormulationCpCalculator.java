package com.agriva.feeding.calculator;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

@Component
public class FormulationCpCalculator {

    private static final int SCALE = 4;
    private static final int RESULT_SCALE = 2;

    /** A single line's contribution: a non-negative weight and a CP%. */
    public record Line(BigDecimal weight, BigDecimal cpPercent) {}

    public BigDecimal weightedAverageCp(Collection<Line> lines) {
        if (lines == null || lines.isEmpty()) return BigDecimal.ZERO;

        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal weightedSum = BigDecimal.ZERO;

        for (Line l : lines) {
            if (l == null || l.weight() == null || l.weight().signum() <= 0) continue;
            BigDecimal cp = l.cpPercent() == null ? BigDecimal.ZERO : l.cpPercent();
            weightedSum = weightedSum.add(l.weight().multiply(cp));
            totalWeight = totalWeight.add(l.weight());
        }

        if (totalWeight.signum() == 0) return BigDecimal.ZERO;

        return weightedSum.divide(totalWeight, SCALE, RoundingMode.HALF_UP)
                .setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }
}