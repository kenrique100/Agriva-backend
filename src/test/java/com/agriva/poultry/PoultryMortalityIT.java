package com.agriva.poultry;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.poultry.model.dto.request.MortalityRecordRequest;
import com.agriva.poultry.model.dto.request.PoultryBatchRequest;
import com.agriva.poultry.model.dto.request.PoultryHouseRequest;
import com.agriva.poultry.model.dto.response.PoultryBatchResponse;
import com.agriva.poultry.model.dto.response.PoultryHouseResponse;
import com.agriva.poultry.model.enums.PoultryBatchStatus;
import com.agriva.poultry.model.enums.PoultrySpecies;
import com.agriva.poultry.repository.PoultryBatchRepository;
import com.agriva.poultry.repository.PoultryHouseRepository;
import com.agriva.poultry.repository.PoultryMortalityRecordRepository;
import com.agriva.poultry.service.PoultryBatchService;
import com.agriva.poultry.service.PoultryHouseService;
import com.agriva.poultry.service.PoultryMortalityService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.InsufficientPoultryStockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PoultryMortalityIT {

    @Autowired PoultryMortalityService mortalityService;
    @Autowired PoultryBatchService batchService;
    @Autowired PoultryHouseService houseService;
    @Autowired AuthService authService;
    @Autowired PoultryMortalityRecordRepository mortalityRepo;
    @Autowired PoultryBatchRepository batchRepo;
    @Autowired PoultryHouseRepository houseRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;
    UUID userId;
    UUID batchId;

    @BeforeEach
    void setUp() {
        mortalityRepo.deleteAll();
        batchRepo.deleteAll();
        houseRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("pmort@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("PMort " + UUID.randomUUID());
        reg.setName("PMort Master");
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
        userId = authRepository.findByEmail("pmort@test.local").orElseThrow().getId();

        PoultryHouseRequest hr = new PoultryHouseRequest();
        hr.setName("Main House");
        PoultryHouseResponse house = houseService.createHouse(hr, farmId, userId);

        PoultryBatchRequest br = new PoultryBatchRequest();
        br.setSpecies(PoultrySpecies.BROILER);
        br.setInitialCount(5000L);
        br.setArrivalDate(LocalDate.now());
        br.setStage("GROWER");
        PoultryBatchResponse batch = batchService.createBatch(house.getId(), br, farmId, userId);
        batchId = batch.getId();
    }

    @Test
    void record_decrementsBatch() {
        MortalityRecordRequest req = req(100L);
        var resp = mortalityService.record(batchId, req, farmId, userId);

        assertThat(resp.getId()).isNotNull();
        assertThat(resp.getQuantity()).isEqualTo(100L);

        var reloaded = batchRepo.findById(batchId).orElseThrow();
        assertThat(reloaded.getCurrentCount()).isEqualTo(4900L);
    }

    @Test
    void record_moreThanCurrentCount_throws422AndLeavesIntact() {
        MortalityRecordRequest req = req(5001L);

        assertThatThrownBy(() -> mortalityService.record(batchId, req, farmId, userId))
                .isInstanceOf(InsufficientPoultryStockException.class)
                .hasMessageContaining("5001")
                .hasMessageContaining("5000");

        var reloaded = batchRepo.findById(batchId).orElseThrow();
        assertThat(reloaded.getCurrentCount()).isEqualTo(5000L);
        assertThat(mortalityRepo.count()).isZero();
    }

    @Test
    void record_exactCurrentCount_zeroesBatch() {
        mortalityService.record(batchId, req(5000L), farmId, userId);
        var reloaded = batchRepo.findById(batchId).orElseThrow();
        assertThat(reloaded.getCurrentCount()).isZero();
    }

    @Test
    void record_onClosedBatch_rejected() {
        batchService.updateStatus(batchId, PoultryBatchStatus.CLOSED, farmId);
        assertThatThrownBy(() -> mortalityService.record(batchId, req(10L), farmId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("non-ACTIVE");
    }

    @Test
    void listForBatch_returnsRowsNewestFirst() {
        mortalityService.record(batchId, reqAt(10L, LocalDate.now().minusDays(2)), farmId, userId);
        mortalityService.record(batchId, reqAt(20L, LocalDate.now()), farmId, userId);

        var page = mortalityService.listForBatch(batchId, farmId,
                org.springframework.data.domain.PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).getQuantity()).isEqualTo(20L);
    }

    private MortalityRecordRequest req(long qty) { return reqAt(qty, LocalDate.now()); }

    private MortalityRecordRequest reqAt(long qty, LocalDate date) {
        MortalityRecordRequest r = new MortalityRecordRequest();
        r.setQuantity(qty);
        r.setRecordedAt(date);
        return r;
    }
}