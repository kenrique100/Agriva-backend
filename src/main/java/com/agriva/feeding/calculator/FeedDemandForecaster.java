package com.agriva.feeding.calculator;

public interface FeedDemandForecaster {

    DemandForecastResult calculate(DemandForecastInput input);

    /** Short, stable identifier persisted on every DemandForecast. */
    String algorithmVersion();
}