package com.agriva.bootstrap;

import com.agriva.TestcontainersConfiguration;
import com.agriva.model.entity.User;
import com.agriva.repository.AuthRepository;
import com.agriva.repository.FarmRepository;
import com.agriva.security.RoleConstants;
import com.agriva.service.impl.AdminBootstrapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AdminBootstrapIT {

    @Autowired AdminBootstrapService adminBootstrapService;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    @BeforeEach
    void setUp() {
        farmRepository.deleteAll();
        authRepository.deleteAll();
        // Recreate the admin so tests can inspect a known state.
        adminBootstrapService.createAdminIfAbsent(
                "admin@test.local", "test-admin", "TestAdminPass1!");
    }

    @Test
    void adminExistsAfterBootstrap() {
        User admin = authRepository.findByEmail("admin@test.local").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(RoleConstants.ADMIN);
        assertThat(admin.getUsername()).isEqualTo("test-admin");
        assertThat(admin.getFarmId()).isNull();
    }

    @Test
    void secondCallIsIdempotent() {
        long before = authRepository.findByRole(RoleConstants.ADMIN).size();
        assertThat(before).isGreaterThanOrEqualTo(1);

        adminBootstrapService.createAdminIfAbsent(
                "admin@test.local", "test-admin", "TestAdminPass1!");

        long after = authRepository.findByRole(RoleConstants.ADMIN).size();
        assertThat(after).isEqualTo(before);
    }

    @Test
    void bootstrapDoesNotOverwriteExistingAdmin() {
        User original = authRepository.findByEmail("admin@test.local").orElseThrow();
        String originalPassword = original.getPassword();

        adminBootstrapService.createAdminIfAbsent(
                "admin@test.local", "some-other-name", "DifferentPass1!");

        User reloaded = authRepository.findByEmail("admin@test.local").orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(original.getId());
        assertThat(reloaded.getUsername()).isEqualTo("test-admin");
        assertThat(reloaded.getPassword()).isEqualTo(originalPassword);
    }

    @Test
    void countByRole_reflectsReality() {
        long admins = adminBootstrapService.countByRole(RoleConstants.ADMIN);
        assertThat(admins).isGreaterThanOrEqualTo(1);
    }
}