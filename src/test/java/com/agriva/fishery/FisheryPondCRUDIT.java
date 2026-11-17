package com.agriva.fishery;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.fishery.model.dto.request.PondRequest;
import com.agriva.fishery.model.dto.request.PondStockRequest;
import com.agriva.fishery.model.dto.response.PondResponse;
import com.agriva.fishery.repository.PondRepository;
import com.agriva.fishery.repository.PondStockRepository;
import com.agriva.fishery.service.PondService;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.PondNotFoundException;
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
class FisheryPondCRUDIT {

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
        reg.setEmail("ponds@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Pond Test Farm " + UUID.randomUUID());
        reg.setName("Pond Master");
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
        userId = authRepository.findByEmail("ponds@test.local").orElseThrow().getId();
    }

    @Test
    void createPond_persistsAndReturns() {
        PondResponse created = pondService.createPond(pondRequest("North Pond"), farmId, userId);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("North Pond");
        assertThat(created.getFarmId()).isEqualTo(farmId);
        assertThat(created.isActive()).isTrue();
        assertThat(created.getStock()).isNull(); // list mapper has no stock
    }

    @Test
    void listPonds_returnsOnlyLivePonds() {
        pondService.createPond(pondRequest("A"), farmId, userId);
        pondService.createPond(pondRequest("B"), farmId, userId);

        var page = pondService.listPonds(farmId, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void getPond_includesStockSummary() {
        PondResponse pond = pondService.createPond(pondRequest("North Pond"), farmId, userId);

        PondStockRequest tilapia = new PondStockRequest();
        tilapia.setSpecies("Tilapia");
        tilapia.setQuantity(5000L);
        tilapia.setAverageWeightG(new BigDecimal("250.00"));
        tilapia.setStage("GROWER");
        pondService.addStock(pond.getId(), tilapia, farmId);

        PondStockRequest catfish = new PondStockRequest();
        catfish.setSpecies("Catfish");
        catfish.setQuantity(200L);
        catfish.setAverageWeightG(new BigDecimal("400.00"));
        catfish.setStage("GROWER");
        pondService.addStock(pond.getId(), catfish, farmId);

        PondResponse detail = pondService.getPond(pond.getId(), farmId);
        assertThat(detail.getStock()).isNotNull();
        assertThat(detail.getStock().getTotalQuantity()).isEqualTo(5200L);
        assertThat(detail.getStock().getSpecies()).containsExactly("Tilapia", "Catfish");
        assertThat(detail.getStock().getDominantStage()).isEqualTo("GROWER");
    }

    @Test
    void duplicatePondNameInSameFarm_rejected() {
        pondService.createPond(pondRequest("North Pond"), farmId, userId);

        assertThatThrownBy(() ->
                pondService.createPond(pondRequest("North Pond"), farmId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updatePond_renames() {
        PondResponse pond = pondService.createPond(pondRequest("Old Name"), farmId, userId);

        PondRequest req = pondRequest("New Name");
        PondResponse updated = pondService.updatePond(pond.getId(), req, farmId);

        assertThat(updated.getName()).isEqualTo("New Name");
    }

    @Test
    void updatePond_duplicateName_rejected() {
        pondService.createPond(pondRequest("A"), farmId, userId);
        PondResponse b = pondService.createPond(pondRequest("B"), farmId, userId);

        assertThatThrownBy(() ->
                pondService.updatePond(b.getId(), pondRequest("A"), farmId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void deletePond_softDeletes() {
        PondResponse pond = pondService.createPond(pondRequest("North"), farmId, userId);

        pondService.deletePond(pond.getId(), farmId);

        assertThatThrownBy(() -> pondService.getPond(pond.getId(), farmId))
                .isInstanceOf(PondNotFoundException.class);

        // Row still in DB with deletedAt set
        var deleted = pondRepo.findById(pond.getId()).orElseThrow();
        assertThat(deleted.getDeletedAt()).isNotNull();
    }

    @Test
    void deletePond_withActiveStock_stillSoftDeletes() {
        PondResponse pond = pondService.createPond(pondRequest("North"), farmId, userId);
        PondStockRequest s = new PondStockRequest();
        s.setSpecies("Tilapia");
        s.setQuantity(1000L);
        pondService.addStock(pond.getId(), s, farmId);

        pondService.deletePond(pond.getId(), farmId); // must not throw

        assertThat(pondRepo.findById(pond.getId()).orElseThrow().getDeletedAt()).isNotNull();
    }

    @Test
    void getPond_unknownId_throwsNotFound() {
        assertThatThrownBy(() -> pondService.getPond(UUID.randomUUID(), farmId))
                .isInstanceOf(PondNotFoundException.class);
    }


    private PondRequest pondRequest(String name) {
        PondRequest r = new PondRequest();
        r.setName(name);
        r.setLocation("Sector A");
        r.setSurfaceAreaM2(new BigDecimal("120.00"));
        r.setDepthM(new BigDecimal("1.50"));
        return r;
    }
}