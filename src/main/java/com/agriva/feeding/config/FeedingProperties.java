package com.agriva.feeding.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agriva.feeding")
public class FeedingProperties {

    /** Feature flags are read directly by @ConditionalOnProperty; declared
     *  here for documentation and future cross-cutting use. */
    private boolean ingredientsEnabled = true;
    private boolean forecastEnabled = true;
    private boolean formulationEnabled = true;
    private boolean productionEnabled = true;
    private boolean consumptionEnabled = true;

    /** Bag size for feed production, used in DR-4. */
    private int defaultBagSizeKg = 50;
}