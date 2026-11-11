package com.agriva.poultry;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.poultry.model.dto.request.PoultryBatchRequest;
import com.agriva.poultry.model.dto.request.PoultryHouseRequest;
import com.agriva.poultry.model.dto.response.PoultryBatchResponse;
import com.agriva.poultry.model.enums.PoultryBatchStatus;
import com.agriva.poultry.model.enums.PoultrySpecies;
import com.agriva.poultry.repository.PoultryBatchRepository;
import com.agriva.poultry.repository.PoultryHouseRepository;
import com.agriva.poultry.service.PoultryBatchService;
import com.agriva.poultry.service.PoultryHouseService;
import com.agriva.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PoultryBatchCRUDIT {

    @Autowired PoultryBatchService batchService;
    @Autowired PoultryHouseService houseService;
    @Autowired AuthService authService;
    @Autowired PoultryHouseRepository houseRepo;
    @Autowired PoultryBatchRepository batchRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;
    UUID userId;
    UUID houseId;

    @BeforeEach
    void setUp() {
        batchRepo.deleteAll();
        houseRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("pbatch@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("PBatch Farm " + UUID.randomUUID());
        reg.setName("PBatch Master");
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
        userId = authRepository.findByEmail("pbatch@test.local").orElseThrow().getId();

        PoultryHouseRequest hr = new PoultryHouseRequest();
        hr.setName("Main House");
        houseId = houseService.createHouse(hr, farmId, userId).getId();
    }

    @Test
    void createBatch_persistsAndReturns() {
        PoultryBatchResponse batch = batchService.createBatch(houseId, batchReq(), farmId, userId);
        assertThat(batch.getId()).isNotNull();
        assertThat(batch.getHouseId()).isEqualTo(houseId);
        assertThat(batch.getSpecies()).isEqualTo(PoultrySpecies.BROILER);
        assertThat(batch.getInitialCount()).isEqualTo(2000L);
        assertThat(batch.getCurrentCount()).isEqualTo(2000L);
        assertThat(batch.getStatus()).isEqualTo(PoultryBatchStatus.ACTIVE);
        assertThat(batch.getHouseName()).isEqualTo("Main House");
    }

    @Test
    void createBatch_unknownHouse_throws() {
        assertThatThrownBy(() ->
                batchService.createBatch(UUID.randomUUID(), batchReq(), farmId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getHouse_aggregatesBatches() {
        batchService.createBatch(houseId, batchReqWith(PoultrySpecies.BROILER, 2000L, "GROWER"),
                farmId, userId);
        batchService.createBatch(houseId, batchReqWith(PoultrySpecies.LAYER, 500L, "LAYER"),
                farmId, userId);

        var house = houseService.getHouse(houseId, farmId);
        assertThat(house.getBatches()).isNotNull();
        assertThat(house.getBatches().getTotalCurrentCount()).isEqualTo(2500L);
        assertThat(house.getBatches().getTotalInitialCount()).isEqualTo(2500L);
        assertThat(house.getBatches().getBatchCount()).isEqualTo(2);
        assertThat(house.getBatches().getSpecies()).containsExactly("BROILER", "LAYER");
        assertThat(house.getBatches().getDominantStage()).isEqualTo("GROWER");
    }

    @Test
    void getHouse_closedBatchNotCounted() {
        var b1 = batchService.createBatch(houseId,
                batchReqWith(PoultrySpecies.BROILER, 1000L, "GROWER"), farmId, userId);
        batchService.createBatch(houseId,
                batchReqWith(PoultrySpecies.LAYER, 500L, "LAYER"), farmId, userId);

        batchService.updateStatus(b1.getId(), PoultryBatchStatus.CLOSED, farmId);

        var house = houseService.getHouse(houseId, farmId);
        assertThat(house.getBatches().getTotalCurrentCount()).isEqualTo(500L);
        assertThat(house.getBatches().getBatchCount()).isEqualTo(1);
        assertThat(house.getBatches().getSpecies()).containsExactly("LAYER");
    }

    @Test
    void updateStatus_closesBatch() {
        var b = batchService.createBatch(houseId, batchReq(), farmId, userId);
        var updated = batchService.updateStatus(b.getId(), PoultryBatchStatus.CLOSED, farmId);
        assertThat(updated.getStatus()).isEqualTo(PoultryBatchStatus.CLOSED);
    }

    @Test
    void listBatchesByHouse_returnsAll() {
        batchService.createBatch(houseId, batchReqWith(PoultrySpecies.BROILER, 100L, "CHICK"),
                farmId, userId);
        batchService.createBatch(houseId, batchReqWith(PoultrySpecies.LAYER, 200L, "CHICK"),
                farmId, userId);
        assertThat(batchService.listBatchesByHouse(houseId, farmId)).hasSize(2);
    }

    @Test
    void listBatchesByFarm_paginates() {
        batchService.createBatch(houseId, batchReqWith(PoultrySpecies.BROILER, 100L, "CHICK"),
                farmId, userId);
        batchService.createBatch(houseId, batchReqWith(PoultrySpecies.LAYER, 200L, "CHICK"),
                farmId, userId);
        var page = batchService.listBatchesByFarm(farmId, PageRequest.of(0, 10));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void batchIsolation_acrossFarms() {
        var batch = batchService.createBatch(houseId, batchReq(), farmId, userId);
        assertThatThrownBy(() ->
                batchService.getBatch(batch.getId(), UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private PoultryBatchRequest batchReq() {
        return batchReqWith(PoultrySpecies.BROILER, 2000L, "GROWER");
    }

    private PoultryBatchRequest batchReqWith(PoultrySpecies species, long count, String stage) {
        PoultryBatchRequest r = new PoultryBatchRequest();
        r.setSpecies(species);
        r.setInitialCount(count);
        r.setArrivalDate(LocalDate.now());
        r.setStage(stage);
        r.setAverageWeightG(new BigDecimal("250.00"));
        return r;
    }
}