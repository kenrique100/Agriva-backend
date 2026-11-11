package com.agriva.fishery;

import com.agriva.TestcontainersConfiguration;
import com.agriva.feeding.port.ConsumerSnapshot;
import com.agriva.feeding.port.ConsumerType;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.fishery.model.dto.request.PondRequest;
import com.agriva.fishery.model.dto.request.PondStockRequest;
import com.agriva.fishery.model.dto.response.PondResponse;
import com.agriva.fishery.repository.PondRepository;
import com.agriva.fishery.repository.PondStockRepository;
import com.agriva.fishery.adapter.FisheryFeedAdapter;
import com.agriva.fishery.service.PondService;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FisheryFeedAdapterIT {

    @Autowired FisheryFeedAdapter adapter;
    @Autowired PondService pondService;
    @Autowired AuthService authService;
    @Autowired PondRepository pondRepo;
    @Autowired PondStockRepository stockRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        stockRepo.deleteAll();
        pondRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("adapter@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Adapter Farm " + UUID.randomUUID());
        reg.setName("Adapter Master");
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
        AuthResponse master = authService.registerMaster(reg);
        farmId = master.getFarmId();
        userId = authRepository.findByEmail("adapter@test.local").orElseThrow().getId();
    }

    @Test
    void consumerType_isFishery() {
        assertThat(adapter.consumerType()).isEqualTo(ConsumerType.FISHERY);
    }

    @Test
    void snapshot_aggregatesHeadcountAndSpecies() {
        PondResponse pond = pondService.createPond(pondRequest("Pond 3"), farmId, userId);
        addStock(pond.getId(), "Tilapia", 5000L, "250.00", "GROWER");
        addStock(pond.getId(), "Catfish", 200L, "400.00", "GROWER");

        Optional<ConsumerSnapshot> snap = adapter.snapshot(farmId, pond.getId());

        assertThat(snap).isPresent();
        ConsumerSnapshot s = snap.get();
        assertThat(s.consumerId()).isEqualTo(pond.getId());
        assertThat(s.headcount()).isEqualTo(5200L);
        assertThat(s.stage()).isEqualTo("GROWER");
        // Dominant species first (Tilapia 5000 > Catfish 200)
        assertThat(s.displayName()).isEqualTo("Pond 3 – Tilapia, Catfish");
    }

    @Test
    void snapshot_deletedPond_returnsEmpty() {
        PondResponse pond = pondService.createPond(pondRequest("Gone"), farmId, userId);
        addStock(pond.getId(), "Tilapia", 1000L, null, null);

        pondService.deletePond(pond.getId(), farmId);

        assertThat(adapter.snapshot(farmId, pond.getId())).isEmpty();
    }

    @Test
    void snapshot_inactivePond_returnsEmpty() {
        PondResponse pond = pondService.createPond(pondRequest("Inactive"), farmId, userId);
        addStock(pond.getId(), "Tilapia", 1000L, null, null);

        var p = pondRepo.findById(pond.getId()).orElseThrow();
        p.setActive(false);
        pondRepo.save(p);

        assertThat(adapter.snapshot(farmId, pond.getId())).isEmpty();
    }

    @Test
    void snapshot_otherFarmsPond_returnsEmpty() {
        PondResponse pond = pondService.createPond(pondRequest("Foreign"), farmId, userId);

        assertThat(adapter.snapshot(UUID.randomUUID(), pond.getId())).isEmpty();
    }

    @Test
    void resolveByDisplayName_fullString_works() {
        PondResponse pond = pondService.createPond(pondRequest("Pond 3"), farmId, userId);
        addStock(pond.getId(), "Tilapia", 5000L, null, null);
        addStock(pond.getId(), "Catfish", 200L, null, null);

        assertThat(adapter.resolveConsumerIdByDisplayName(farmId, "Pond 3 – Tilapia, Catfish"))
                .contains(pond.getId());
    }

    @Test
    void resolveByDisplayName_barePondName_works() {
        PondResponse pond = pondService.createPond(pondRequest("Pond 9"), farmId, userId);
        addStock(pond.getId(), "Tilapia", 100L, null, null);

        assertThat(adapter.resolveConsumerIdByDisplayName(farmId, "Pond 9"))
                .contains(pond.getId());
    }

    @Test
    void resolveByDisplayName_unknown_returnsEmpty() {
        assertThat(adapter.resolveConsumerIdByDisplayName(farmId, "Nonexistent")).isEmpty();
    }


    private PondRequest pondRequest(String name) {
        PondRequest r = new PondRequest();
        r.setName(name);
        return r;
    }

    private void addStock(UUID pondId, String species, long qty, String avgWeight, String stage) {
        PondStockRequest s = new PondStockRequest();
        s.setSpecies(species);
        s.setQuantity(qty);
        s.setAverageWeightG(avgWeight != null ? new BigDecimal(avgWeight) : null);
        s.setStage(stage);
        pondService.addStock(pondId, s, farmId);
    }
}