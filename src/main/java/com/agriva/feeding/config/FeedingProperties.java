package com.agriva.feeding.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agriva.feeding")
public class FeedingProperties {

    private boolean ingredientsEnabled = true;
    private boolean forecastEnabled = true;
    private boolean formulationEnabled = true;
    private boolean productionEnabled = true;
    private boolean consumptionEnabled = true;
    private int defaultBagSizeKg = 50;
}