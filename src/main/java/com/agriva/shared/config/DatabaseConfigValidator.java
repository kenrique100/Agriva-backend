package com.agriva.shared.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseConfigValidator {

    private static final String LEGACY_PLACEHOLDER = "Bundes9898";
    private static final int MIN_JWT_SECRET_LENGTH = 64;   // 512 bits for HS512
    private static final Set<String> RELAXED_PROFILES = Set.of("dev", "test", "local");

    private final Environment env;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${agriva.security.jwt.secret:}")
    private String jwtSecret;

    @PostConstruct
    void validate() {
        boolean relaxed = Arrays.stream(env.getActiveProfiles())
                .anyMatch(RELAXED_PROFILES::contains);

        // Legacy DB placeholder
        if (LEGACY_PLACEHOLDER.equals(datasourcePassword)) {
            if (!relaxed) {
                throw new IllegalStateException(
                        "Refusing to start: spring.datasource.password matches the legacy "
                                + "dev placeholder '" + LEGACY_PLACEHOLDER + "'. Set DB_PASSWORD "
                                + "to a strong value before running with profiles "
                                + Arrays.toString(env.getActiveProfiles()) + ".");
            }
            log.warn("spring.datasource.password uses the legacy dev placeholder — "
                            + "acceptable in {} but must be replaced before any deployment.",
                    Arrays.toString(env.getActiveProfiles()));
        }

        // Legacy Redis placeholder
        if (LEGACY_PLACEHOLDER.equals(redisPassword)) {
            if (!relaxed) {
                throw new IllegalStateException(
                        "Refusing to start: spring.data.redis.password matches the legacy "
                                + "dev placeholder '" + LEGACY_PLACEHOLDER + "'. Set "
                                + "REDIS_PASSWORD to a strong value.");
            }
            log.warn("spring.data.redis.password uses the legacy dev placeholder — "
                    + "acceptable in {} only.", Arrays.toString(env.getActiveProfiles()));
        }

        if (!relaxed) {
            if (datasourcePassword == null || datasourcePassword.isBlank()) {
                throw new IllegalStateException(
                        "Refusing to start: DB_PASSWORD is empty in profile "
                                + Arrays.toString(env.getActiveProfiles()) + ".");
            }
            if (jwtSecret == null || jwtSecret.length() < MIN_JWT_SECRET_LENGTH) {
                throw new IllegalStateException(
                        "Refusing to start: JWT_SECRET is missing or shorter than "
                                + MIN_JWT_SECRET_LENGTH + " characters (HS512 requires a "
                                + "512-bit key). Current length: "
                                + (jwtSecret == null ? "null" : jwtSecret.length()) + ".");
            }
        }
    }
}