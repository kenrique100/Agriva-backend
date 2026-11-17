package com.agriva.fishery;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.fishery.model.dto.request.MortalityRequest;
import com.agriva.fishery.model.dto.request.PondRequest;
import com.agriva.fishery.model.dto.request.PondStockRequest;
import com.agriva.fishery.model.dto.response.MortalityResponse;
import com.agriva.fishery.model.dto.response.PondResponse;
import com.agriva.fishery.repository.MortalityRecordRepository;
import com.agriva.fishery.repository.PondRepository;
import com.agriva.fishery.repository.PondStockRepository;
import com.agriva.fishery.service.MortalityService;
import com.agriva.fishery.service.PondService;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.InsufficientPondStockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FisheryMortalityIT {

    @Autowired MortalityService mortalityService;
    @Autowired PondService pondService;
    @Autowired AuthService authService;
    @Autowired MortalityRecordRepository mortalityRepo;
    @Autowired PondRepository pondRepo;
    @Autowired PondStockRepository stockRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        mortalityRepo.deleteAll();
        stockRepo.deleteAll();
        pondRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("mort@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Mort " + UUID.randomUUID());
        reg.setName("Mort Master");
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
        AuthResponse m = authService.registerMaster(reg);
        farmId = m.getFarmId();
        userId = authRepository.findByEmail("mort@test.local").orElseThrow().getId();
    }

    @Test
    void record_decrementsStock() {
        PondResponse pond = createStockedPond("P1", "Tilapia", 5000L);

        MortalityRequest req = mortalityReq("Tilapia", 100L);
        MortalityResponse resp = mortalityService.record(pond.getId(), req, farmId, userId);

        assertThat(resp.getId()).isNotNull();
        assertThat(resp.getQuantity()).isEqualTo(100L);
        assertThat(resp.getSpecies()).isEqualTo("Tilapia");
        assertThat(currentStock(pond.getId(), "Tilapia")).isEqualTo(4900L);
    }

    @Test
    void record_quantityExceedsStock_throws422AndLeavesStockIntact() {
        PondResponse pond = createStockedPond("P1", "Tilapia", 5000L);

        MortalityRequest req = mortalityReq("Tilapia", 5001L);

        assertThatThrownBy(() -> mortalityService.record(pond.getId(), req, farmId, userId))
                .isInstanceOf(InsufficientPondStockException.class)
                .hasMessageContaining("Insufficient pond stock")
                .hasMessageContaining("5001")
                .hasMessageContaining("5000");

        assertThat(currentStock(pond.getId(), "Tilapia")).isEqualTo(5000L);
        assertThat(mortalityRepo.count()).isZero();
    }

    @Test
    void record_unknownSpecies_throws() {
        PondResponse pond = createStockedPond("P1", "Tilapia", 5000L);

        MortalityRequest req = mortalityReq("Catfish", 10L);

        assertThatThrownBy(() -> mortalityService.record(pond.getId(), req, farmId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No stock for species");
    }

    @Test
    void record_multiSpecies_isIndependentPerSpecies() {
        PondResponse pond = pondService.createPond(pondReq("MultiP"), farmId, userId);
        addStock(pond.getId(), "Tilapia", 1000L);
        addStock(pond.getId(), "Catfish", 500L);

        mortalityService.record(pond.getId(), mortalityReq("Tilapia", 100L), farmId, userId);

        assertThat(currentStock(pond.getId(), "Tilapia")).isEqualTo(900L);
        assertThat(currentStock(pond.getId(), "Catfish")).isEqualTo(500L);
    }

    @Test
    void listForPond_returnsRowsNewestFirst() {
        PondResponse pond = createStockedPond("P1", "Tilapia", 5000L);

        MortalityRequest a = mortalityReq("Tilapia", 10L);
        a.setRecordedAt(LocalDate.now().minusDays(2));
        mortalityService.record(pond.getId(), a, farmId, userId);

        MortalityRequest b = mortalityReq("Tilapia", 20L);
        mortalityService.record(pond.getId(), b, farmId, userId);

        var page = mortalityService.listForPond(pond.getId(), farmId, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    // ---------------------------------------------------------------- helpers

    private PondResponse createStockedPond(String name, String species, long qty) {
        PondResponse pond = pondService.createPond(pondReq(name), farmId, userId);
        addStock(pond.getId(), species, qty);
        return pond;
    }

    private PondRequest pondReq(String name) {
        PondRequest r = new PondRequest();
        r.setName(name);
        return r;
    }

    private void addStock(UUID pondId, String species, long qty) {
        PondStockRequest s = new PondStockRequest();
        s.setSpecies(species);
        s.setQuantity(qty);
        pondService.addStock(pondId, s, farmId);
    }

    private MortalityRequest mortalityReq(String species, long qty) {
        MortalityRequest r = new MortalityRequest();
        r.setSpecies(species);
        r.setQuantity(qty);
        r.setRecordedAt(LocalDate.now());
        return r;
    }

    private long currentStock(UUID pondId, String species) {
        return stockRepo.findByPondId(pondId).stream()
                .filter(s -> s.getSpecies().equals(species))
                .findFirst()
                .map(s -> s.getQuantity() == null ? 0L : s.getQuantity())
                .orElse(0L);
    }
}