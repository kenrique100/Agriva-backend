package com.agriva.feeding.calculator;

public interface FeedDemandForecaster {

    DemandForecastResult calculate(DemandForecastInput input);

    String algorithmVersion();
}