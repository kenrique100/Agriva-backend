package com.agriva.poultry;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.feeding.port.ConsumerSnapshot;
import com.agriva.feeding.port.ConsumerType;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.poultry.adapter.PoultryFeedAdapter;
import com.agriva.poultry.model.dto.request.MortalityRecordRequest;
import com.agriva.poultry.model.dto.request.PoultryBatchRequest;
import com.agriva.poultry.model.dto.request.PoultryHouseRequest;
import com.agriva.poultry.model.dto.response.PoultryBatchResponse;
import com.agriva.poultry.model.dto.response.PoultryHouseResponse;
import com.agriva.poultry.model.enums.PoultrySpecies;
import com.agriva.poultry.repository.PoultryBatchRepository;
import com.agriva.poultry.repository.PoultryHouseRepository;
import com.agriva.poultry.service.PoultryBatchService;
import com.agriva.poultry.service.PoultryHouseService;
import com.agriva.poultry.service.PoultryMortalityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PoultryFeedAdapterIT {

    @Autowired PoultryFeedAdapter adapter;
    @Autowired PoultryHouseService houseService;
    @Autowired PoultryBatchService batchService;
    @Autowired PoultryMortalityService mortalityService;
    @Autowired AuthService authService;
    @Autowired PoultryHouseRepository houseRepo;
    @Autowired PoultryBatchRepository batchRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        batchRepo.deleteAll();
        houseRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("padapter@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("PAdapter Farm " + UUID.randomUUID());
        reg.setName("PAdapter Master");
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
        userId = authRepository.findByEmail("padapter@test.local").orElseThrow().getId();
    }

    @Test
    void consumerType_isPoultry() {
        assertThat(adapter.consumerType()).isEqualTo(ConsumerType.POULTRY);
    }

    @Test
    void snapshot_aggregatesActiveBatches() {
        var house = createHouse("House A");
        createBatch(house.getId(), PoultrySpecies.BROILER, 2000L, "GROWER");
        createBatch(house.getId(), PoultrySpecies.LAYER, 500L, "LAYER");

        Optional<ConsumerSnapshot> snap = adapter.snapshot(farmId, house.getId());
        assertThat(snap).isPresent();
        assertThat(snap.get().consumerId()).isEqualTo(house.getId());
        assertThat(snap.get().headcount()).isEqualTo(2500L);
        assertThat(snap.get().displayName()).isEqualTo("House A – BROILER, LAYER");
        assertThat(snap.get().stage()).isEqualTo("GROWER"); // dominant by count
    }

    @Test
    void snapshot_noBatches_returnsZeroHeadcount() {
        var house = createHouse("Empty House");
        Optional<ConsumerSnapshot> snap = adapter.snapshot(farmId, house.getId());
        assertThat(snap).isPresent();
        assertThat(snap.get().headcount()).isZero();
        assertThat(snap.get().displayName()).isEqualTo("Empty House");
        assertThat(snap.get().stage()).isNull();
    }

    @Test
    void snapshot_closedBatches_ignored() {
        var house = createHouse("House B");
        var b1 = createBatch(house.getId(), PoultrySpecies.BROILER, 1000L, "GROWER");
        createBatch(house.getId(), PoultrySpecies.LAYER, 500L, "LAYER");
        batchService.updateStatus(b1.getId(),
                com.agriva.poultry.model.enums.PoultryBatchStatus.CLOSED, farmId);

        Optional<ConsumerSnapshot> snap = adapter.snapshot(farmId, house.getId());
        assertThat(snap).isPresent();
        assertThat(snap.get().headcount()).isEqualTo(500L);
        assertThat(snap.get().displayName()).isEqualTo("House B – LAYER");
    }

    @Test
    void snapshot_deletedHouse_returnsEmpty() {
        var house = createHouse("Gone");
        createBatch(house.getId(), PoultrySpecies.BROILER, 100L, "GROWER");
        houseService.deleteHouse(house.getId(), farmId);
        assertThat(adapter.snapshot(farmId, house.getId())).isEmpty();
    }

    @Test
    void snapshot_inactiveHouse_returnsEmpty() {
        var house = createHouse("Inactive");
        var entity = houseRepo.findById(house.getId()).orElseThrow();
        entity.setActive(false);
        houseRepo.save(entity);
        assertThat(adapter.snapshot(farmId, house.getId())).isEmpty();
    }

    @Test
    void snapshot_otherFarmsHouse_returnsEmpty() {
        var house = createHouse("Foreign");
        assertThat(adapter.snapshot(UUID.randomUUID(), house.getId())).isEmpty();
    }

    @Test
    void snapshot_afterMortality_reflectsDecrement() {
        var house = createHouse("Mortality House");
        var batch = createBatch(house.getId(), PoultrySpecies.BROILER, 5000L, "GROWER");

        var beforeSnap = adapter.snapshot(farmId, house.getId()).orElseThrow();
        assertThat(beforeSnap.headcount()).isEqualTo(5000L);

        MortalityRecordRequest req = new MortalityRecordRequest();
        req.setQuantity(100L);
        req.setRecordedAt(LocalDate.now());
        mortalityService.record(batch.getId(), req, farmId, userId);

        var afterSnap = adapter.snapshot(farmId, house.getId()).orElseThrow();
        assertThat(afterSnap.headcount()).isEqualTo(4900L);
    }

    @Test
    void snapshot_afterMultipleMortalities_reflectsCumulativeDecrement() {
        var house = createHouse("MultiMortality House");
        var batch = createBatch(house.getId(), PoultrySpecies.BROILER, 1000L, "GROWER");

        mortalityService.record(batch.getId(), mortalityReq(50L, LocalDate.now().minusDays(2)),
                farmId, userId);
        mortalityService.record(batch.getId(), mortalityReq(25L, LocalDate.now().minusDays(1)),
                farmId, userId);
        mortalityService.record(batch.getId(), mortalityReq(125L, LocalDate.now()),
                farmId, userId);

        var snap = adapter.snapshot(farmId, house.getId()).orElseThrow();
        assertThat(snap.headcount()).isEqualTo(800L); // 1000 - 50 - 25 - 125
    }

    @Test
    void resolveByDisplayName_fullString_works() {
        var house = createHouse("House 5");
        createBatch(house.getId(), PoultrySpecies.BROILER, 100L, "GROWER");
        assertThat(adapter.resolveConsumerIdByDisplayName(farmId, "House 5 – BROILER"))
                .contains(house.getId());
    }

    @Test
    void resolveByDisplayName_bareName_works() {
        var house = createHouse("House 6");
        createBatch(house.getId(), PoultrySpecies.BROILER, 100L, "GROWER");
        assertThat(adapter.resolveConsumerIdByDisplayName(farmId, "House 6"))
                .contains(house.getId());
    }

    @Test
    void resolveByDisplayName_unknown_returnsEmpty() {
        assertThat(adapter.resolveConsumerIdByDisplayName(farmId, "Nonexistent"))
                .isEmpty();
    }

    @Test
    void resolveByDisplayName_hyphenSuffix_works() {
        var house = createHouse("House 7");
        createBatch(house.getId(), PoultrySpecies.LAYER, 100L, "LAYER");
        assertThat(adapter.resolveConsumerIdByDisplayName(farmId, "House 7 - LAYER"))
                .contains(house.getId());
    }

    private PoultryHouseResponse createHouse(String name) {
        PoultryHouseRequest hr = new PoultryHouseRequest();
        hr.setName(name);
        return houseService.createHouse(hr, farmId, userId);
    }

    private PoultryBatchResponse createBatch(UUID houseId, PoultrySpecies species,
                                             long count, String stage) {
        PoultryBatchRequest br = new PoultryBatchRequest();
        br.setSpecies(species);
        br.setInitialCount(count);
        br.setArrivalDate(LocalDate.now().minusDays(5));
        br.setStage(stage);
        return batchService.createBatch(houseId, br, farmId, userId);
    }

    private MortalityRecordRequest mortalityReq(long qty, LocalDate date) {
        MortalityRecordRequest r = new MortalityRecordRequest();
        r.setQuantity(qty);
        r.setRecordedAt(date);
        return r;
    }
}