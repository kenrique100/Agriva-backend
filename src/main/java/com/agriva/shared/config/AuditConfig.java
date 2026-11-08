package com.agriva.shared.config;

import com.agriva.shared.security.AuthenticatedUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * The ONLY place @EnableJpaAuditing is declared.
 * <p>
 * If you ever need to disable auditing (e.g. for a specific test profile),
 * use spring.data.jpa.auditing.enabled=false instead of deleting the
 * annotation, so the bean graph stays consistent.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class AuditConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return Optional.of("system");
            if (auth.getPrincipal() instanceof AuthenticatedUser u) return Optional.of(u.email());
            return Optional.ofNullable(auth.getName()).or(() -> Optional.of("system"));
        };
    }
}