package com.agriva.feeding.calculator;

import org.springframework.stereotype.Component;

/**
 * Default {@link FeedDemandForecaster} until the user's Calculator A lands.
 * <p>
 * When the user provides their own {@code @Component @Primary
 * implements FeedDemandForecaster}, Spring will resolve the injection point
 * to that bean and this one becomes dormant.
 */
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