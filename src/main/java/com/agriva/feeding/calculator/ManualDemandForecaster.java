package com.agriva.feeding.calculator;

import org.springframework.stereotype.Component;

@Component
public class ManualDemandForecaster implements FeedDemandForecaster {

    @Override
    public DemandForecastResult calculate(DemandForecastInput input) {
        throw new UnsupportedOperationException(
                "AUTOMATIC forecast requires the production algorithm — "
                        + "the FeedDemandForecaster is not yet wired in");
    }

    @Override
    public String algorithmVersion() {
        return "manual-v0-unavailable";
    }
}