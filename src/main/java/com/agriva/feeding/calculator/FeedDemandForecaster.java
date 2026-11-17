package com.agriva.feeding.calculator;

/**
 * Reserved slot for the user's Calculator A.
 * <p>
 * Answers: "how much feed will N heads consume from initialWeight to
 * targetWeight at the given temperature?"
 * <p>
 * PURE PLANNING. Implementations MUST NOT touch ingredient stock or the
 * farm feed store. Side effects belong to the production layer.
 * <p>
 * Ship-safe default: {@code ManualDemandForecaster} throws
 * {@link UnsupportedOperationException} (mapped to 501) until the user
 * provides their implementation with {@code @Primary}.
 */
public interface FeedDemandForecaster {

    DemandForecastResult calculate(DemandForecastInput input);

    /** Short, stable identifier persisted on every DemandForecast. */
    String algorithmVersion();
}