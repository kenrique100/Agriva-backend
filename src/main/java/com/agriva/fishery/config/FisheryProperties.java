package com.agriva.fishery.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agriva.fishery")
public class FisheryProperties {
    /** Feature flag; also honored by {@code @ConditionalOnProperty} on the controller. */
    private boolean enabled = true;
}