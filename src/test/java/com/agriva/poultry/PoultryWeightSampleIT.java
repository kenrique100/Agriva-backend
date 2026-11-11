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
import com.agriva.poultry.model.dto.request.WeightSampleRequest;
import com.agriva.poultry.model.dto.response.PoultryBatchResponse;
import com.agriva.poultry.model.dto.response.PoultryHouseResponse;
import com.agriva.poultry.model.dto.response.WeightSampleResponse;
import com.agriva.poultry.model.enums.PoultryBatchStatus;
import com.agriva.poultry.model.enums.PoultrySpecies;
import com.agriva.poultry.repository.PoultryBatchRepository;
import com.agriva.poultry.repository.PoultryHouseRepository;
import com.agriva.poultry.repository.PoultryWeightSampleRepository;
import com.agriva.poultry.service.PoultryBatchService;
import com.agriva.poultry.service.PoultryHouseService;
import com.agriva.poultry.service.PoultryWeightSampleService;
import com.agriva.shared.exception.BusinessException;
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
class PoultryWeightSampleIT {

    @Autowired PoultryWeightSampleService sampleService;
    @Autowired PoultryBatchService batchService;
    @Autowired PoultryHouseService houseService;
    @Autowired AuthService authService;
    @Autowired PoultryWeightSampleRepository sampleRepo;
    @Autowired PoultryBatchRepository batchRepo;
    @Autowired PoultryHouseRepository houseRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;
    UUID userId;
    UUID batchId;

    @BeforeEach
    void setUp() {
        sampleRepo.deleteAll();
        batchRepo.deleteAll();
        houseRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("pweight@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("PWeight " + UUID.randomUUID());
        reg.setName("PWeight Master");
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
        userId = authRepository.findByEmail("pweight@test.local").orElseThrow().getId();

        // Batch created with 50g baseline; DR-8b seeds initialAvgWeightG from request.
        PoultryHouseRequest hr = new PoultryHouseRequest();
        hr.setName("Weigh House");
        PoultryHouseResponse house = houseService.createHouse(hr, farmId, userId);

        PoultryBatchRequest br = new PoultryBatchRequest();
        br.setSpecies(PoultrySpecies.BROILER);
        br.setInitialCount(2000L);
        br.setArrivalDate(LocalDate.now().minusDays(10));
        br.setStage("GROWER");
        br.setAverageWeightG(new BigDecimal("50.00"));
        PoultryBatchResponse batch = batchService.createBatch(house.getId(), br, farmId, userId);
        batchId = batch.getId();
    }

    @Test
    void record_firstSample_updatesAverageAndSeedsBaseline() {
        // Baseline starts at 50g (from createBatch), average same.
        var reloadedBefore = batchRepo.findById(batchId).orElseThrow();
        assertThat(reloadedBefore.getInitialAvgWeightG()).isEqualByComparingTo("50.00");
        assertThat(reloadedBefore.getAverageWeightG()).isEqualByComparingTo("50.00");

        WeightSampleRequest req = weightReq(new BigDecimal("250.00"), 50);
        WeightSampleResponse resp = sampleService.record(batchId, req, farmId, userId);

        assertThat(resp.getId()).isNotNull();
        assertThat(resp.getAverageWeightG()).isEqualByComparingTo("250.00");
        assertThat(resp.getSampleSize()).isEqualTo(50);

        var reloaded = batchRepo.findById(batchId).orElseThrow();
        assertThat(reloaded.getAverageWeightG()).isEqualByComparingTo("250.00");
        // Baseline not overwritten — it was already seeded on create.
        assertThat(reloaded.getInitialAvgWeightG()).isEqualByComparingTo("50.00");
    }

    @Test
    void record_batchWithNullBaseline_firstSampleSeedsIt() {
        // Second batch created with null averageWeightG (explicitly omitted)
        PoultryHouseRequest hr = new PoultryHouseRequest();
        hr.setName("NoBaseline House");
        PoultryHouseResponse house = houseService.createHouse(hr, farmId, userId);

        PoultryBatchRequest br = new PoultryBatchRequest();
        br.setSpecies(PoultrySpecies.BROILER);
        br.setInitialCount(1000L);
        br.setArrivalDate(LocalDate.now().minusDays(5));
        br.setStage("CHICK");
        // deliberately no averageWeightG
        PoultryBatchResponse freshBatch = batchService.createBatch(house.getId(), br, farmId, userId);

        assertThat(batchRepo.findById(freshBatch.getId()).orElseThrow().getInitialAvgWeightG())
                .isNull();

        sampleService.record(freshBatch.getId(),
                weightReq(new BigDecimal("120.00"), 30), farmId, userId);

        var reloaded = batchRepo.findById(freshBatch.getId()).orElseThrow();
        assertThat(reloaded.getAverageWeightG()).isEqualByComparingTo("120.00");
        assertThat(reloaded.getInitialAvgWeightG()).isEqualByComparingTo("120.00");
    }

    @Test
    void record_secondSample_updatesAverageKeepsBaseline() {
        sampleService.record(batchId, weightReq(new BigDecimal("250.00"), 50), farmId, userId);
        sampleService.record(batchId, weightReq(new BigDecimal("500.00"), 50), farmId, userId);

        var reloaded = batchRepo.findById(batchId).orElseThrow();
        assertThat(reloaded.getAverageWeightG()).isEqualByComparingTo("500.00");
        assertThat(reloaded.getInitialAvgWeightG()).isEqualByComparingTo("50.00"); // unchanged
    }

    @Test
    void record_onClosedBatch_rejected() {
        batchService.updateStatus(batchId, PoultryBatchStatus.CLOSED, farmId);

        assertThatThrownBy(() -> sampleService.record(
                batchId, weightReq(new BigDecimal("300.00"), 10), farmId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("non-ACTIVE");
    }

    @Test
    void record_onCulledBatch_rejected() {
        batchService.updateStatus(batchId, PoultryBatchStatus.CULLED, farmId);

        assertThatThrownBy(() -> sampleService.record(
                batchId, weightReq(new BigDecimal("300.00"), 10), farmId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("non-ACTIVE");
    }

    @Test
    void record_unknownBatch_throws() {
        assertThatThrownBy(() -> sampleService.record(
                UUID.randomUUID(), weightReq(new BigDecimal("100.00"), 10), farmId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void record_otherFarm_throws() {
        assertThatThrownBy(() -> sampleService.record(
                batchId, weightReq(new BigDecimal("100.00"), 10), UUID.randomUUID(), userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listForBatch_returnsRowsNewestFirst() {
        sampleService.record(batchId,
                weightReqAt(new BigDecimal("100.00"), 20, LocalDate.now().minusDays(5)),
                farmId, userId);
        sampleService.record(batchId,
                weightReqAt(new BigDecimal("300.00"), 30, LocalDate.now()),
                farmId, userId);

        var page = sampleService.listForBatch(batchId, farmId, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).getAverageWeightG()).isEqualByComparingTo("300.00");
        assertThat(page.getContent().get(1).getAverageWeightG()).isEqualByComparingTo("100.00");
    }

    private WeightSampleRequest weightReq(BigDecimal avgG, int sampleSize) {
        return weightReqAt(avgG, sampleSize, LocalDate.now());
    }

    private WeightSampleRequest weightReqAt(BigDecimal avgG, int sampleSize, LocalDate date) {
        WeightSampleRequest r = new WeightSampleRequest();
        r.setAverageWeightG(avgG);
        r.setSampleSize(sampleSize);
        r.setSampledAt(date);
        return r;
    }
}