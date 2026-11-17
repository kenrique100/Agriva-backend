package com.agriva.feeding.calculator;

public enum FormulationMode {
    /** Calculator decides the recipe from targets + available ingredients. */
    ALGORITHM,
    /** Caller supplies ingredient lines directly; calculator computes totals. */
    CUSTOM
}