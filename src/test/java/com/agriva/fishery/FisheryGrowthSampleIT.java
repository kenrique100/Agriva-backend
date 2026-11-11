package com.agriva.fishery;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.feeding.port.ConsumerSnapshot;
import com.agriva.fishery.adapter.FisheryFeedAdapter;
import com.agriva.fishery.model.dto.request.GrowthSampleRequest;
import com.agriva.fishery.model.dto.request.PondRequest;
import com.agriva.fishery.model.dto.request.PondStockRequest;
import com.agriva.fishery.model.dto.response.GrowthSampleResponse;
import com.agriva.fishery.model.dto.response.PondResponse;
import com.agriva.fishery.repository.GrowthSampleRepository;
import com.agriva.fishery.repository.PondRepository;
import com.agriva.fishery.repository.PondStockRepository;
import com.agriva.fishery.service.GrowthSampleService;
import com.agriva.fishery.service.PondService;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FisheryGrowthSampleIT {

    @Autowired GrowthSampleService growthSampleService;
    @Autowired PondService pondService;
    @Autowired
    FisheryFeedAdapter adapter;
    @Autowired AuthService authService;
    @Autowired GrowthSampleRepository growthRepo;
    @Autowired PondRepository pondRepo;
    @Autowired PondStockRepository stockRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        growthRepo.deleteAll();
        stockRepo.deleteAll();
        pondRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("growth@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Growth " + UUID.randomUUID());
        reg.setName("Growth Master");
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
        userId = authRepository.findByEmail("growth@test.local").orElseThrow().getId();
    }

    @Test
    void record_updatesPondStockAverageWeight() {
        PondResponse pond = createStockedPond("P1", "Tilapia", 5000L);

        GrowthSampleRequest req = sampleReq("Tilapia", "250.00", 50);
        GrowthSampleResponse resp = growthSampleService.record(
                pond.getId(), req, farmId, userId);

        assertThat(resp.getId()).isNotNull();
        assertThat(resp.getAverageWeightG()).isEqualByComparingTo("250.00");
        assertThat(resp.getSampleSize()).isEqualTo(50);

        BigDecimal updatedAvg = stockRepo.findByPondId(pond.getId()).stream()
                .filter(s -> s.getSpecies().equals("Tilapia"))
                .findFirst()
                .map(s -> s.getAverageWeightG())
                .orElseThrow();
        assertThat(updatedAvg).isEqualByComparingTo("250.00");
    }

    @Test
    void record_unknownSpecies_throws() {
        PondResponse pond = createStockedPond("P1", "Tilapia", 5000L);

        GrowthSampleRequest req = sampleReq("Catfish", "100.00", 10);

        assertThatThrownBy(() -> growthSampleService.record(pond.getId(), req, farmId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No stock for species");
    }

    @Test
    void adapter_derivesStageFromLatestSample() {
        PondResponse pond = createStockedPond("P1", "Tilapia", 5000L);

        // 30g → FINGERLING
        growthSampleService.record(pond.getId(),
                sampleReq("Tilapia", "30.00", 50), farmId, userId);
        assertThat(snapshotStage(pond.getId())).isEqualTo("FINGERLING");

        // 150g → JUVENILE
        growthSampleService.record(pond.getId(),
                sampleReq("Tilapia", "150.00", 50), farmId, userId);
        assertThat(snapshotStage(pond.getId())).isEqualTo("JUVENILE");

        // 350g → GROWER
        growthSampleService.record(pond.getId(),
                sampleReq("Tilapia", "350.00", 50), farmId, userId);
        assertThat(snapshotStage(pond.getId())).isEqualTo("GROWER");

        // 600g → ADULT
        growthSampleService.record(pond.getId(),
                sampleReq("Tilapia", "600.00", 50), farmId, userId);
        assertThat(snapshotStage(pond.getId())).isEqualTo("ADULT");
    }

    @Test
    void adapter_fallsBackToPondStockStageWhenNoSamples() {
        PondResponse pond = pondService.createPond(pondReq("NoSamples"), farmId, userId);
        PondStockRequest s = new PondStockRequest();
        s.setSpecies("Tilapia");
        s.setQuantity(1000L);
        s.setStage("GROWER");
        pondService.addStock(pond.getId(), s, farmId);

        assertThat(snapshotStage(pond.getId())).isEqualTo("GROWER");
    }

    @Test
    void listForPond_returnsRows() {
        PondResponse pond = createStockedPond("P1", "Tilapia", 5000L);
        growthSampleService.record(pond.getId(),
                sampleReq("Tilapia", "100.00", 20), farmId, userId);
        growthSampleService.record(pond.getId(),
                sampleReq("Tilapia", "150.00", 30), farmId, userId);

        var page = growthSampleService.listForPond(pond.getId(), farmId,
                PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    // ---------------------------------------------------------------- helpers

    private PondResponse createStockedPond(String name, String species, long qty) {
        PondResponse pond = pondService.createPond(pondReq(name), farmId, userId);
        PondStockRequest s = new PondStockRequest();
        s.setSpecies(species);
        s.setQuantity(qty);
        pondService.addStock(pond.getId(), s, farmId);
        return pond;
    }

    private PondRequest pondReq(String name) {
        PondRequest r = new PondRequest();
        r.setName(name);
        return r;
    }

    private GrowthSampleRequest sampleReq(String species, String avgG, int sampleSize) {
        GrowthSampleRequest r = new GrowthSampleRequest();
        r.setSpecies(species);
        r.setAverageWeightG(new BigDecimal(avgG));
        r.setSampleSize(sampleSize);
        r.setSampledAt(LocalDate.now());
        return r;
    }

    private String snapshotStage(UUID pondId) {
        Optional<ConsumerSnapshot> s = adapter.snapshot(farmId, pondId);
        assertThat(s).isPresent();
        return s.get().stage();
    }
}