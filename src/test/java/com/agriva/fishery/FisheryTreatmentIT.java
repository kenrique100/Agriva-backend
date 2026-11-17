package com.agriva.fishery;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.fishery.model.dto.request.PondRequest;
import com.agriva.fishery.model.dto.request.TreatmentRequest;
import com.agriva.fishery.model.dto.response.PondResponse;
import com.agriva.fishery.model.dto.response.TreatmentResponse;
import com.agriva.fishery.repository.PondRepository;
import com.agriva.fishery.repository.PondStockRepository;
import com.agriva.fishery.repository.TreatmentRepository;
import com.agriva.fishery.service.PondService;
import com.agriva.fishery.service.TreatmentService;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.shared.exception.PondNotFoundException;
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
class FisheryTreatmentIT {

    @Autowired TreatmentService treatmentService;
    @Autowired PondService pondService;
    @Autowired AuthService authService;
    @Autowired TreatmentRepository treatmentRepo;
    @Autowired PondRepository pondRepo;
    @Autowired PondStockRepository stockRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        treatmentRepo.deleteAll();
        stockRepo.deleteAll();
        pondRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("treat@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Treat " + UUID.randomUUID());
        reg.setName("Treat Master");
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
        userId = authRepository.findByEmail("treat@test.local").orElseThrow().getId();
    }

    @Test
    void record_lists_and_deletes() {
        PondResponse pond = createPond("Pond T1");

        TreatmentRequest req = new TreatmentRequest();
        req.setMedicationName("Oxytetracycline");
        req.setDosage(new BigDecimal("10.0"));
        req.setDosageUnit("mg/L");
        req.setAdministeredAt(LocalDate.now());
        req.setWithdrawalDays(14);
        req.setNotes("Prophylactic");

        TreatmentResponse created = treatmentService.record(pond.getId(), req, farmId, userId);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getMedicationName()).isEqualTo("Oxytetracycline");
        assertThat(created.getDosage()).isEqualByComparingTo("10.0");
        assertThat(created.getDosageUnit()).isEqualTo("mg/L");
        assertThat(created.getWithdrawalDays()).isEqualTo(14);
        assertThat(created.getAdministeredByUserId()).isEqualTo(userId);

        var page = treatmentService.listForPond(pond.getId(), farmId, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(1);

        treatmentService.delete(pond.getId(), created.getId(), farmId);
        assertThat(treatmentService.listForPond(pond.getId(), farmId,
                PageRequest.of(0, 20)).getTotalElements()).isZero();
    }

    @Test
    void record_unknownPond_throws() {
        TreatmentRequest req = new TreatmentRequest();
        req.setMedicationName("X");
        req.setDosage(new BigDecimal("1.0"));
        req.setDosageUnit("mg/L");
        req.setAdministeredAt(LocalDate.now());

        assertThatThrownBy(() -> treatmentService.record(UUID.randomUUID(), req, farmId, userId))
                .isInstanceOf(PondNotFoundException.class);
    }

    @Test
    void delete_wrongPond_throws() {
        PondResponse a = createPond("Pond A");
        PondResponse b = createPond("Pond B");

        TreatmentRequest req = new TreatmentRequest();
        req.setMedicationName("X");
        req.setDosage(new BigDecimal("1.0"));
        req.setDosageUnit("mg/L");
        req.setAdministeredAt(LocalDate.now());
        TreatmentResponse created = treatmentService.record(a.getId(), req, farmId, userId);

        assertThatThrownBy(() -> treatmentService.delete(b.getId(), created.getId(), farmId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private PondResponse createPond(String name) {
        PondRequest r = new PondRequest();
        r.setName(name);
        return pondService.createPond(r, farmId, userId);
    }
}