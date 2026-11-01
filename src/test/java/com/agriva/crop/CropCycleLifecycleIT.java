package com.agriva.crop;

import com.agriva.TestcontainersConfiguration;
import com.agriva.exception.BusinessException;
import com.agriva.model.dto.auth.AuthResponse;
import com.agriva.model.dto.auth.MasterRegisterRequest;
import com.agriva.model.dto.crop.*;
import com.agriva.model.entity.enums.Gender;
import com.agriva.model.entity.enums.CropCycleStatus;
import com.agriva.repository.*;
import com.agriva.service.AuthService;
import com.agriva.service.CropManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CropCycleLifecycleIT {

    @Autowired CropManagementService cropService;
    @Autowired AuthService authService;

    @Autowired FieldRepository fieldRepo;
    @Autowired CropRepository cropRepo;
    @Autowired CropCycleRepository cycleRepo;
    @Autowired CropActivityRepository activityRepo;
    @Autowired HarvestRepository harvestRepo;
    @Autowired StagedCropCycleRepository stagedRepo;

    @Autowired SalaryPaymentRepository salaryRepository;
    @Autowired TaskRepository taskRepository;
    @Autowired PersonnelStatusHistoryRepository historyRepository;
    @Autowired PersonnelRepository personnelRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired InvestmentRepository investmentRepository;
    @Autowired IncomeRepository incomeRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;

    @BeforeEach
    void setUp() {
        harvestRepo.deleteAll();
        activityRepo.deleteAll();
        cycleRepo.deleteAll();
        fieldRepo.deleteAll();
        cropRepo.deleteAll();
        stagedRepo.deleteAll();

        salaryRepository.deleteAll();
        taskRepository.deleteAll();
        historyRepository.deleteAll();
        personnelRepository.deleteAll();
        expenseRepository.deleteAll();
        investmentRepository.deleteAll();
        incomeRepository.deleteAll();
        transactionRepository.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("lifecycle@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Lifecycle Farm " + UUID.randomUUID());
        reg.setName("Lifecycle Master");
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
    }

    @Test
    void happyPath_plantedToGrowingToHarvested() {
        CropCycleResponse cycle = createCycle("North Plot", "Maize", 90);
        UUID cycleId = cycle.getId();

        CropCycleResponse growing = cropService.updateCycleStatus(cycleId, "GROWING", farmId);
        assertThat(growing.getStatus()).isEqualTo("GROWING");

        HarvestRequest harvest = new HarvestRequest();
        harvest.setHarvestDate(LocalDate.now());
        harvest.setQuantity(new BigDecimal("1200.50"));
        harvest.setUnit("KG");
        harvest.setQualityGrade("Grade A");
        cropService.logHarvest(cycleId, harvest, farmId);

        var persisted = cycleRepo.findById(cycleId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(CropCycleStatus.HARVESTED);
        assertThat(persisted.getActualHarvestDate()).isEqualTo(LocalDate.now());

        var harvests = harvestRepo.findByCropCycleId(cycleId);
        assertThat(harvests).hasSize(1);
        assertThat(harvests.get(0).getQuantity()).isEqualByComparingTo("1200.50");
    }

    @Test
    void failedStatus_canBeSetDirectly() {
        CropCycleResponse cycle = createCycle("North Plot", "Maize", 90);

        CropCycleResponse failed = cropService.updateCycleStatus(
                cycle.getId(), "FAILED", farmId);

        assertThat(failed.getStatus()).isEqualTo("FAILED");

        var persisted = cycleRepo.findById(cycle.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(CropCycleStatus.FAILED);
    }

    @Test
    void invalidStatus_throwsBusinessException() {
        CropCycleResponse cycle = createCycle("North Plot", "Maize", 90);

        assertThatThrownBy(() ->
                cropService.updateCycleStatus(cycle.getId(), "NOT_A_STATUS", farmId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid cycle status");
    }

    @Test
    void statusIsCaseInsensitive() {
        CropCycleResponse cycle = createCycle("North Plot", "Maize", 90);

        CropCycleResponse growing = cropService.updateCycleStatus(
                cycle.getId(), "growing", farmId);
        assertThat(growing.getStatus()).isEqualTo("GROWING");
    }

    @Test
    void harvestWithoutPriorGrowing_stillSetsHarvested() {
        CropCycleResponse cycle = createCycle("North Plot", "Maize", 90);

        HarvestRequest harvest = new HarvestRequest();
        harvest.setHarvestDate(LocalDate.now());
        harvest.setQuantity(new BigDecimal("100.00"));
        harvest.setUnit("KG");
        cropService.logHarvest(cycle.getId(), harvest, farmId);

        var persisted = cycleRepo.findById(cycle.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(CropCycleStatus.HARVESTED);
    }

    private CropCycleResponse createCycle(String fieldName, String cropName, int days) {
        FieldRequest fieldReq = new FieldRequest();
        fieldReq.setName(fieldName);
        fieldReq.setSizeHectares(new BigDecimal("1.00"));
        FieldResponse field = cropService.createField(fieldReq, farmId);

        CropRequest cropReq = new CropRequest();
        cropReq.setName(cropName);
        cropReq.setExpectedDaysToHarvest(days);
        CropResponse crop = cropService.createCrop(cropReq, farmId);

        CropCycleRequest cycleReq = new CropCycleRequest();
        cycleReq.setFieldId(field.getId());
        cycleReq.setCropId(crop.getId());
        cycleReq.setPlantingDate(LocalDate.now());
        return cropService.createCycle(cycleReq, farmId);
    }
}