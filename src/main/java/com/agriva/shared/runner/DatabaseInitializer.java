package com.agriva.shared.runner;

import com.agriva.shared.security.RoleConstants;
import com.agriva.identity.service.impl.AdminBootstrapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {

    private final AdminBootstrapService adminBootstrapService;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        log.info("Initializing database with default admin user...");
        // Proxy is applied because we call another bean — REQUIRES_NEW works now.
        adminBootstrapService.createAdminIfAbsent(adminEmail, adminUsername, adminPassword);

        log.info("Stats - Masters: {}, SubUsers: {}, Admins: {}",
                adminBootstrapService.countByRole(RoleConstants.MASTER),
                adminBootstrapService.countByRole(RoleConstants.SUB_USER),
                adminBootstrapService.countByRole(RoleConstants.ADMIN));
    }
}