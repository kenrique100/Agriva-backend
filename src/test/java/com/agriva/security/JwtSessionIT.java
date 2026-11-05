package com.agriva.security;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.models.dto.request.LoginRequest;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.repository.PersonnelRepository;
import com.agriva.personnel.repository.PersonnelStatusHistoryRepository;
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

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "agriva.security.jwt.access-token-ttl-seconds=86400"
})
class JwtSessionIT {

    @Autowired AuthService authService;
    @Autowired ObjectMapper objectMapper;
    @Autowired
    AuthRepository authRepository;
    @Autowired
    FarmRepository farmRepository;
    @Autowired
    PersonnelRepository personnelRepository;
    @Autowired
    PersonnelStatusHistoryRepository historyRepository;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        personnelRepository.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("ttl@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("TTL Farm");
        reg.setName("TTL Master");
        reg.setGender(Gender.MALE);
        reg.setDateOfBirth(LocalDate.now().minusYears(35));
        reg.setPhoneNumber("+237600000000");
        reg.setCountry("Cameroon");
        reg.setState("Centre");
        reg.setCity("Yaoundé");
        reg.setAddress("Rue 1");
        reg.setPostalCode("00000");
        reg.setCurrency("XAF");
        reg.setUserAgreement(true);
        authService.registerMaster(reg);
    }

    @Test
    void accessToken_ttlIs24Hours() throws Exception {
        LoginRequest login = new LoginRequest();
        login.setEmailOrUsername("ttl@test.local");
        login.setPassword("MasterPass1!");

        AuthResponse session = authService.login(login);
        String token = session.getToken();

        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode claims = objectMapper.readTree(payload);

        long iat = claims.get("iat").asLong();
        long exp = claims.get("exp").asLong();

        assertThat(exp - iat).isEqualTo(86400L);

        // jti must be present (blacklist key)
        assertThat(claims.get("jti").asText()).isNotBlank();
        // iat should be very close to "now" — sanity check the claim we now
        // read in JwtAuthFilter (audit A10)
        long nowSec = System.currentTimeMillis() / 1000L;
        assertThat(Math.abs(nowSec - iat)).isLessThan(10);
    }
}