package com.agriva.auth;

import com.agriva.TestcontainersConfiguration;
import com.agriva.model.dto.auth.*;
import com.agriva.repository.AuthRepository;
import com.agriva.repository.FarmRepository;
import com.agriva.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthServiceIT {

    @Autowired AuthService authService;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        authRepository.deleteAll();
        farmRepository.deleteAll();
    }

    @Test
    void registerMaster_thenLogin_succeeds() {
        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("master@test.local");
        reg.setUsername("master_test");
        reg.setPassword("MasterPass1");
        reg.setFarmName("Test Farm");

        AuthResponse created = authService.registerMaster(reg);

        assertThat(created.getEmail()).isEqualTo("master@test.local");
        assertThat(created.getRole()).isEqualTo("MASTER");
        assertThat(created.getFarmId()).isNotNull();
        assertThat(created.getToken()).isNotBlank();

        LoginRequest login = new LoginRequest();
        login.setEmailOrUsername("master@test.local");
        login.setPassword("MasterPass1");

        String token = authService.login(login);
        assertThat(token).isNotBlank();
    }

    @Test
    void registerMaster_duplicateEmail_rejected() {
        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("dup@test.local");
        reg.setPassword("MasterPass1");
        reg.setFarmName("Farm A");
        authService.registerMaster(reg);

        MasterRegisterRequest dup = new MasterRegisterRequest();
        dup.setEmail("dup@test.local");
        dup.setPassword("MasterPass2");
        dup.setFarmName("Farm B");

        assertThatThrownBy(() -> authService.registerMaster(dup))
                .hasMessageContaining("Email already in use");
    }

    @Test
    void registerSubUser_succeedsWhenFarmExists() {
        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("master2@test.local");
        reg.setPassword("MasterPass1");
        reg.setFarmName("Farm With Sub");

        AuthResponse master = authService.registerMaster(reg);

        SubUserRegisterRequest sub = new SubUserRegisterRequest();
        sub.setEmail("sub@test.local");
        sub.setUsername("sub_test");
        sub.setPassword("SubPass123");
        sub.setFarmId(master.getFarmId());

        String result = authService.registerSubUser(sub);
        assertThat(result).contains("sub@test.local");
    }

    @Test
    void changePassword_validFlow() {
        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("pwd@test.local");
        reg.setPassword("MasterPass1");
        reg.setFarmName("Pwd Farm");
        AuthResponse created = authService.registerMaster(reg);

        var user = authRepository.findByEmail("pwd@test.local").orElseThrow();

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("MasterPass1");
        req.setNewPassword("NewPass456");

        authService.changePassword(user.getId(), req);

        var updated = authRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass456", updated.getPassword())).isTrue();
    }
}