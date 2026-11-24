package com.agriva.feeding.calculator;

/**
 * Strategy interface for feed-demand forecasting algorithms.
 * <p>
 * Implementations:
 *   - FishDemandForecaster    (@Primary) — handles CATFISH and TILAPIA
 *   - TilapiaDemandForecaster (secondary) — strict TILAPIA-only, opt-in
 * <p>
 * The single bean injected into ForecastServiceImpl is FishDemandForecaster
 * because it is @Primary and covers both supported species.
 */
public interface FeedDemandForecaster {

    /**
     * Calculate growth stages + total feed demand for the given input.
     *
     * @throws com.agriva.shared.exception.BusinessException
     *         if the species is unsupported or inputs are invalid
     */
    DemandForecastResult calculate(DemandForecastInput input);

    /**
     * Version string stamped on every DemandForecast row so we can trace
     * which algorithm produced it. Used for reproducibility.
     */
    String algorithmVersion();
}