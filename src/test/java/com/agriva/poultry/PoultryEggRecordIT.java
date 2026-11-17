package com.agriva.poultry;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.poultry.model.dto.request.EggRecordRequest;
import com.agriva.poultry.model.dto.request.PoultryBatchRequest;
import com.agriva.poultry.model.dto.request.PoultryHouseRequest;
import com.agriva.poultry.model.dto.response.EggRecordResponse;
import com.agriva.poultry.model.dto.response.PoultryHouseResponse;
import com.agriva.poultry.model.enums.PoultryBatchStatus;
import com.agriva.poultry.model.enums.PoultrySpecies;
import com.agriva.poultry.repository.PoultryBatchRepository;
import com.agriva.poultry.repository.PoultryEggRecordRepository;
import com.agriva.poultry.repository.PoultryHouseRepository;
import com.agriva.poultry.service.PoultryBatchService;
import com.agriva.poultry.service.PoultryEggRecordService;
import com.agriva.poultry.service.PoultryHouseService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.ResourceNotFoundException;
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
class PoultryEggRecordIT {

    @Autowired PoultryEggRecordService eggService;
    @Autowired PoultryBatchService batchService;
    @Autowired PoultryHouseService houseService;
    @Autowired AuthService authService;
    @Autowired PoultryEggRecordRepository eggRepo;
    @Autowired PoultryBatchRepository batchRepo;
    @Autowired PoultryHouseRepository houseRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;
    UUID userId;
    UUID broilerBatchId;
    UUID layerHouseId;

    @BeforeEach
    void setUp() {
        eggRepo.deleteAll();
        batchRepo.deleteAll();
        houseRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("pegg@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("PEgg " + UUID.randomUUID());
        reg.setName("PEgg Master");
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
        userId = authRepository.findByEmail("pegg@test.local").orElseThrow().getId();

        // Broiler batch (used for "wrong species" rejection tests)
        PoultryHouseRequest brHouseReq = new PoultryHouseRequest();
        brHouseReq.setName("Broiler House");
        PoultryHouseResponse brHouse = houseService.createHouse(brHouseReq, farmId, userId);

        PoultryBatchRequest brReq = new PoultryBatchRequest();
        brReq.setSpecies(PoultrySpecies.BROILER);
        brReq.setInitialCount(2000L);
        brReq.setArrivalDate(LocalDate.now().minusDays(10));
        brReq.setStage("GROWER");
        broilerBatchId = batchService.createBatch(brHouse.getId(), brReq, farmId, userId).getId();

        // Layer house (happy path)
        PoultryHouseRequest lhReq = new PoultryHouseRequest();
        lhReq.setName("Layer House");
        PoultryHouseResponse lh = houseService.createHouse(lhReq, farmId, userId);
        layerHouseId = lh.getId();
    }

    @Test
    void record_layerBatch_succeeds() {
        UUID layerBatchId = createBatch(layerHouseId, PoultrySpecies.LAYER, 500L, "LAYER");

        EggRecordRequest req = eggReq(400, 10, LocalDate.now());
        EggRecordResponse resp = eggService.record(layerBatchId, req, farmId, userId);

        assertThat(resp.getId()).isNotNull();
        assertThat(resp.getBatchId()).isEqualTo(layerBatchId);
        assertThat(resp.getEggsCollected()).isEqualTo(400);
        assertThat(resp.getBrokenEggs()).isEqualTo(10);
        assertThat(resp.getFarmId()).isEqualTo(farmId);
    }

    @Test
    void record_duckBatch_succeeds() {
        UUID duckBatchId = createBatch(layerHouseId, PoultrySpecies.DUCK, 200L, "LAYER");

        EggRecordRequest req = eggReq(50, 0, LocalDate.now());
        EggRecordResponse resp = eggService.record(duckBatchId, req, farmId, userId);

        assertThat(resp.getEggsCollected()).isEqualTo(50);
    }

    @Test
    void record_broilerBatch_rejected() {
        EggRecordRequest req = eggReq(100, 0, LocalDate.now());

        assertThatThrownBy(() -> eggService.record(broilerBatchId, req, farmId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("LAYER")
                .hasMessageContaining("BROILER");
    }

    @Test
    void record_cockerelBatch_rejected() {
        UUID cockerelBatchId = createBatch(layerHouseId, PoultrySpecies.COCKEREL, 200L, "GROWER");

        EggRecordRequest req = eggReq(10, 0, LocalDate.now());

        assertThatThrownBy(() -> eggService.record(cockerelBatchId, req, farmId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("LAYER");
    }

    @Test
    void record_brokenExceedsCollected_rejected() {
        UUID layerBatchId = createBatch(layerHouseId, PoultrySpecies.LAYER, 500L, "LAYER");

        EggRecordRequest req = eggReq(10, 20, LocalDate.now());

        assertThatThrownBy(() -> eggService.record(layerBatchId, req, farmId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("brokenEggs");
    }

    @Test
    void record_onClosedBatch_rejected() {
        UUID layerBatchId = createBatch(layerHouseId, PoultrySpecies.LAYER, 500L, "LAYER");
        batchService.updateStatus(layerBatchId, PoultryBatchStatus.CLOSED, farmId);

        EggRecordRequest req = eggReq(100, 0, LocalDate.now());

        assertThatThrownBy(() -> eggService.record(layerBatchId, req, farmId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("non-ACTIVE");
    }

    @Test
    void record_brokenEggsNull_defaultsToZero() {
        UUID layerBatchId = createBatch(layerHouseId, PoultrySpecies.LAYER, 500L, "LAYER");

        EggRecordRequest req = new EggRecordRequest();
        req.setEggsCollected(100);
        req.setRecordedAt(LocalDate.now());
        // brokenEggs left null

        EggRecordResponse resp = eggService.record(layerBatchId, req, farmId, userId);
        assertThat(resp.getBrokenEggs()).isZero();
    }

    @Test
    void record_unknownBatch_throws() {
        EggRecordRequest req = eggReq(10, 0, LocalDate.now());

        assertThatThrownBy(() -> eggService.record(UUID.randomUUID(), req, farmId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listForBatch_returnsRowsNewestFirst() {
        UUID layerBatchId = createBatch(layerHouseId, PoultrySpecies.LAYER, 500L, "LAYER");
        eggService.record(layerBatchId, eggReq(100, 0, LocalDate.now().minusDays(3)),
                farmId, userId);
        eggService.record(layerBatchId, eggReq(250, 2, LocalDate.now()),
                farmId, userId);

        var page = eggService.listForBatch(layerBatchId, farmId, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).getEggsCollected()).isEqualTo(250);
        assertThat(page.getContent().get(1).getEggsCollected()).isEqualTo(100);
    }

    private UUID createBatch(UUID houseId, PoultrySpecies species, long count, String stage) {
        PoultryBatchRequest br = new PoultryBatchRequest();
        br.setSpecies(species);
        br.setInitialCount(count);
        br.setArrivalDate(LocalDate.now().minusDays(5));
        br.setStage(stage);
        return batchService.createBatch(houseId, br, farmId, userId).getId();
    }

    private EggRecordRequest eggReq(int collected, int broken, LocalDate date) {
        EggRecordRequest r = new EggRecordRequest();
        r.setEggsCollected(collected);
        r.setBrokenEggs(broken);
        r.setRecordedAt(date);
        return r;
    }
}