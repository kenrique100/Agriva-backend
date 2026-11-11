package com.agriva.feeding.calculator;

import java.util.Set;

/**
 * Reserved slot for the user's Calculator B.
 * <p>
 * Answers: "what ingredients, in what amounts, make up this recipe?"
 * <p>
 * PURE PLANNING. Implementations MUST NOT touch ingredient stock. The stock
 * check happens in {@code FormulationService} AFTER the calculator returns.
 * <p>
 * Ship-safe default: {@code ManualFeedFormulationCalculator} handles
 * CUSTOM mode; ALGORITHM mode throws {@link UnsupportedOperationException}
 * (mapped to 501) until the user provides their implementation with
 * {@code @Primary}.
 */
public interface FeedFormulationCalculator {

    FormulationResult calculate(FormulationInput input);

    String algorithmVersion();

    /** Modes this calculator can handle; empty is invalid. */
    Set<FormulationMode> supportedModes();
}