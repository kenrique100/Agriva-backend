package com.agriva.poultry.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "agriva.poultry")
public class PoultryProperties {

    /** Master feature flag; also honored by @ConditionalOnProperty on controllers/adapter. */
    private boolean enabled = true;

    /** Reserved for DR-8c — declared now for forward compatibility. */
    private boolean healthEnabled = false;
}