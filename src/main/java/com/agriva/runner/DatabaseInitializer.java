package com.agriva.runner;

import com.agriva.model.entity.User;
import com.agriva.repository.AuthRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {

    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.username}")
    private String adminUsername;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        log.info("Initializing database with default admin user...");

        // Read-only check (own transaction, short-lived)
        boolean adminExists = authRepository.findByEmail(adminEmail).isPresent();

        if (!adminExists) {
            log.info("Creating default admin user...");
            createAdmin();
            log.info("Default admin created with email: {}", adminEmail);
        } else {
            log.info("Admin already exists with email: {}", adminEmail);
        }

        log.info("Stats - Masters: {}, SubUsers: {}, Admins: {}",
                authRepository.findByRole("MASTER").size(),
                authRepository.findByRole("SUB_USER").size(),
                authRepository.findByRole("ADMIN").size());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void createAdmin() {
        User admin = User.builder()
                .email(adminEmail)
                .username(adminUsername)
                .password(passwordEncoder.encode(adminPassword))
                .role("ADMIN")
                .farmId(null)
                .build();
        authRepository.save(admin);
    }
}