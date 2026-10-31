package com.agriva;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Deliberately does NOT carry @EnableJpaAuditing.
 * Auditing is enabled exactly once, on AuditConfig, with an explicit
 * auditorAwareRef, so the spaAuditingHandler bean is created a single time.
 */
@SpringBootApplication
@EnableScheduling
public class AgrivaApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgrivaApplication.class, args);
    }
}