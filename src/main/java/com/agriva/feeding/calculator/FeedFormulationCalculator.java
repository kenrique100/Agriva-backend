package com.agriva.feeding.calculator;

import java.util.Set;

public interface FeedFormulationCalculator {

    FormulationResult calculate(FormulationInput input);

    String algorithmVersion();

    /** Modes this calculator can handle; empty is invalid. */
    Set<FormulationMode> supportedModes();
}