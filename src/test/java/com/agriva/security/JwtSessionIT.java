package com.agriva.security;

import com.agriva.TestcontainersConfiguration;
import com.agriva.model.dto.auth.LoginRequest;
import com.agriva.model.dto.auth.MasterRegisterRequest;
import com.agriva.model.entity.enums.Gender;
import com.agriva.service.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the session-TTL fix. Uses an explicit 86400s override so it
 * exercises the production value, not the test-default 900s.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "agriva.security.jwt.access-token-ttl-seconds=86400"
})
class JwtSessionIT {

    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;
    @Autowired com.agriva.repository.AuthRepository authRepository;
    @Autowired com.agriva.repository.FarmRepository farmRepository;
    @Autowired com.agriva.repository.PersonnelRepository personnelRepository;
    @Autowired com.agriva.repository.PersonnelStatusHistoryRepository historyRepository;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        personnelRepository.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("ttl@test.local");
        reg.setPassword("MasterPass1");
        reg.setFarmName("TTL Farm");
        reg.setName("TTL Master");
        reg.setGender(Gender.MALE);
        reg.setDateOfBirth(LocalDate.now().minusYears(35));
        reg.setPhoneNumber("+237600000000");
        reg.setCountry("Cameroon"); reg.setState("Centre"); reg.setCity("Yaoundé");
        reg.setAddress("Rue 1"); reg.setPostalCode("00000");
        reg.setCurrency("XAF"); reg.setUserAgreement(true);
        authService.registerMaster(reg);
    }

    @Test
    void accessToken_ttlIs24Hours() throws Exception {
        LoginRequest login = new LoginRequest();
        login.setEmailOrUsername("ttl@test.local");
        login.setPassword("MasterPass1");
        String token = authService.login(login);

        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode claims = objectMapper.readTree(payload);

        long iat = claims.get("iat").asLong();
        long exp = claims.get("exp").asLong();

        assertThat(exp - iat).isEqualTo(86400L);   // 24 hours
    }
}