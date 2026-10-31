package com.agriva.auth;

import com.agriva.TestcontainersConfiguration;
import com.agriva.model.dto.auth.*;
import com.agriva.model.entity.enums.Gender;
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

import java.time.LocalDate;

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

    private MasterRegisterRequest masterReg(String email, String password, String farmName) {
        MasterRegisterRequest r = new MasterRegisterRequest();
        r.setEmail(email);
        r.setUsername(email.split("@")[0]);
        r.setPassword(password);
        r.setFarmName(farmName);
        r.setName("Master " + email.split("@")[0]);
        r.setGender(Gender.MALE);
        r.setDateOfBirth(LocalDate.now().minusYears(35));
        r.setPhoneNumber("+237600000000");
        r.setCountry("Cameroon");
        r.setState("Centre");
        r.setCity("Yaoundé");
        r.setAddress("Rue 1");
        r.setPostalCode("00000");
        r.setCurrency("XAF");
        r.setUserAgreement(true);
        return r;
    }

    private SubUserRegisterRequest subReg(String email, String password) {
        SubUserRegisterRequest r = new SubUserRegisterRequest();
        r.setEmail(email);
        r.setUsername(email.split("@")[0]);
        r.setPassword(password);
        r.setName("Sub " + email.split("@")[0]);
        r.setGender(Gender.FEMALE);
        r.setDateOfBirth(LocalDate.now().minusYears(25));
        r.setPhoneNumber("+237600000001");
        r.setAddress("Rue 2");
        return r;
    }

    @Test
    void registerMaster_thenLogin_succeeds() {
        AuthResponse created = authService.registerMaster(
                masterReg("master@test.local", "MasterPass1", "Test Farm"));

        assertThat(created.getEmail()).isEqualTo("master@test.local");
        assertThat(created.getRole()).isEqualTo("MASTER");
        assertThat(created.getFarmId()).isNotNull();
        assertThat(created.getToken()).isNotBlank();
        assertThat(created.getRefreshToken()).isNotBlank();

        LoginRequest login = new LoginRequest();
        login.setEmailOrUsername("master@test.local");
        login.setPassword("MasterPass1");

        AuthResponse session = authService.login(login);
        assertThat(session.getToken()).isNotBlank();
        assertThat(session.getRefreshToken()).isNotBlank();
        assertThat(session.getRole()).isEqualTo("MASTER");
    }

    @Test
    void registerMaster_duplicateEmail_rejected() {
        authService.registerMaster(
                masterReg("dup@test.local", "MasterPass1", "Farm A"));

        assertThatThrownBy(() ->
                authService.registerMaster(masterReg("dup@test.local", "MasterPass2", "Farm B")))
                .hasMessageContaining("Email already in use");
    }

    @Test
    void registerSubUser_succeedsWhenFarmExists() {
        AuthResponse master = authService.registerMaster(
                masterReg("master2@test.local", "MasterPass1", "Farm With Sub"));

        // S1 fix: farmId and role now come from the authenticated principal, not the DTO.
        SubUserRegisterRequest sub = subReg("sub@test.local", "SubPass123");

        SubUserRegisteredResponse created = authService.registerSubUser(
                sub, master.getFarmId(), "MASTER");

        assertThat(created.userId()).isNotNull();
        assertThat(created.email()).isEqualTo("sub@test.local");
        assertThat(created.farmId()).isEqualTo(master.getFarmId());
    }

    @Test
    void changePassword_validFlow() {
        authService.registerMaster(masterReg("pwd@test.local", "MasterPass1", "Pwd Farm"));

        var user = authRepository.findByEmail("pwd@test.local").orElseThrow();

        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword("MasterPass1");
        req.setNewPassword("NewPass456");

        authService.changePassword(user.getId(), req);

        var updated = authRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass456", updated.getPassword())).isTrue();
    }
}