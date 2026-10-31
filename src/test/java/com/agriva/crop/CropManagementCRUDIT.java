package com.agriva.crop;

import com.agriva.TestcontainersConfiguration;
import com.agriva.exception.BusinessException;
import com.agriva.exception.ResourceNotFoundException;
import com.agriva.model.dto.auth.AuthResponse;
import com.agriva.model.dto.auth.MasterRegisterRequest;
import com.agriva.model.dto.crop.*;
import com.agriva.model.entity.enums.ActivityType;
import com.agriva.model.entity.enums.Gender;
import com.agriva.model.entity.enums.SoilType;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CropManagementCRUDIT {

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
    UUID userId;

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
        reg.setEmail("crop@test.local");
        reg.setPassword("MasterPass1");
        reg.setFarmName("Crop Farm " + UUID.randomUUID());
        reg.setName("Crop Master");
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
        userId = authRepository.findByEmail("crop@test.local").orElseThrow().getId();
    }

    @Test
    void createField_persistsAndReturns() {
        FieldRequest req = new FieldRequest();
        req.setName("North Plot");
        req.setSizeHectares(new BigDecimal("2.50"));
        req.setSoilType(SoilType.LOAMY);

        FieldResponse created = cropService.createField(req, farmId);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("North Plot");
        assertThat(created.getSizeHectares()).isEqualByComparingTo("2.50");
        assertThat(created.getSoilType()).isEqualTo(SoilType.LOAMY);

        List<FieldResponse> list = cropService.getFields(farmId);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getName()).isEqualTo("North Plot");
    }

    @Test
    void createField_duplicateName_throwsBusinessException() {
        FieldRequest req = new FieldRequest();
        req.setName("Dup Field");
        cropService.createField(req, farmId);

        FieldRequest dup = new FieldRequest();
        dup.setName("Dup Field");

        assertThatThrownBy(() -> cropService.createField(dup, farmId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void getFields_emptyFarm_returnsEmptyList() {
        assertThat(cropService.getFields(farmId)).isEmpty();
    }

    @Test
    void createCrop_persistsAndReturns() {
        CropRequest req = new CropRequest();
        req.setName("Maize");
        req.setVariety("Hybrid 123");
        req.setExpectedDaysToHarvest(90);

        CropResponse created = cropService.createCrop(req, farmId);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Maize");
        assertThat(created.getVariety()).isEqualTo("Hybrid 123");
        assertThat(created.getExpectedDaysToHarvest()).isEqualTo(90);

        assertThat(cropService.getCrops(farmId)).hasSize(1);
    }

    @Test
    void createCrop_minimalFields_succeeds() {
        CropRequest req = new CropRequest();
        req.setName("Cassava");

        CropResponse created = cropService.createCrop(req, farmId);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getExpectedDaysToHarvest()).isNull();
    }

    @Test
    void createCycle_computesExpectedHarvestDate() {
        FieldResponse field = createField("Field A");
        CropResponse crop = createCrop("Maize", 90);

        CropCycleRequest req = new CropCycleRequest();
        req.setFieldId(field.getId());
        req.setCropId(crop.getId());
        req.setPlantingDate(LocalDate.now());
        req.setNotes("Spring planting");

        CropCycleResponse created = cropService.createCycle(req, farmId);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getFieldId()).isEqualTo(field.getId());
        assertThat(created.getCropId()).isEqualTo(crop.getId());
        assertThat(created.getFieldName()).isEqualTo("Field A");
        assertThat(created.getCropName()).isEqualTo("Maize");
        assertThat(created.getExpectedHarvestDate())
                .isEqualTo(created.getPlantingDate().plusDays(90));
        assertThat(created.getNotes()).isEqualTo("Spring planting");
        assertThat(created.getStatus()).isIn("PLANNED", "PLANTED");
    }

    @Test
    void createCycle_missingField_throws() {
        CropResponse crop = createCrop("Maize", 90);

        CropCycleRequest req = new CropCycleRequest();
        req.setFieldId(UUID.randomUUID());
        req.setCropId(crop.getId());
        req.setPlantingDate(LocalDate.now());

        assertThatThrownBy(() -> cropService.createCycle(req, farmId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Field not found");
    }

    @Test
    void createCycle_missingCrop_throws() {
        FieldResponse field = createField("Field A");

        CropCycleRequest req = new CropCycleRequest();
        req.setFieldId(field.getId());
        req.setCropId(UUID.randomUUID());
        req.setPlantingDate(LocalDate.now());

        assertThatThrownBy(() -> cropService.createCycle(req, farmId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Crop not found");
    }

    @Test
    void getCycle_notFound_throws() {
        assertThatThrownBy(() -> cropService.getCycle(UUID.randomUUID(), farmId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Crop cycle not found");
    }

    @Test
    void getCycles_returnsAllCyclesForFarm() {
        createCycle("Field A", "Maize", 90);
        createCycle("Field B", "Tomato", 60);

        List<CropCycleResponse> cycles = cropService.getCycles(farmId);
        assertThat(cycles).hasSize(2);
        assertThat(cycles).extracting(CropCycleResponse::getCropName)
                .containsExactlyInAnyOrder("Maize", "Tomato");
    }

    @Test
    void logActivity_persistsAndReturns() {
        CropCycleResponse cycle = createCycle("Field A", "Maize", 90);

        CropActivityRequest req = new CropActivityRequest();
        req.setActivityType(ActivityType.FERTILIZER);
        req.setActivityDate(LocalDate.now());
        req.setNotes("NPK applied at 50kg/ha");

        CropActivityResponse created = cropService.logActivity(
                cycle.getId(), req, farmId, userId);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getCropCycleId()).isEqualTo(cycle.getId());
        assertThat(created.getActivityType()).isEqualTo("FERTILIZER");
        assertThat(created.getActivityDate()).isEqualTo(LocalDate.now());
        assertThat(created.getNotes()).isEqualTo("NPK applied at 50kg/ha");
    }

    @Test
    void getActivities_ordersByDateDescending() {
        CropCycleResponse cycle = createCycle("Field A", "Maize", 90);

        CropActivityRequest older = new CropActivityRequest();
        older.setActivityType(ActivityType.IRRIGATION);
        older.setActivityDate(LocalDate.now().minusDays(3));
        cropService.logActivity(cycle.getId(), older, farmId, userId);

        CropActivityRequest newer = new CropActivityRequest();
        newer.setActivityType(ActivityType.WEEDING);
        newer.setActivityDate(LocalDate.now());
        cropService.logActivity(cycle.getId(), newer, farmId, userId);

        List<CropActivityResponse> list = cropService.getActivities(cycle.getId(), farmId);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getActivityType()).isEqualTo("WEEDING");
        assertThat(list.get(1).getActivityType()).isEqualTo("IRRIGATION");
    }

    @Test
    void logHarvest_marksCycleHarvested() {
        CropCycleResponse cycle = createCycle("Field A", "Maize", 90);

        HarvestRequest req = new HarvestRequest();
        req.setHarvestDate(LocalDate.now());
        req.setQuantity(new BigDecimal("500.00"));
        req.setUnit("KG");
        req.setQualityGrade("Grade A");

        HarvestResponse harvest = cropService.logHarvest(cycle.getId(), req, farmId);

        assertThat(harvest.getId()).isNotNull();
        assertThat(harvest.getCropCycleId()).isEqualTo(cycle.getId());
        assertThat(harvest.getQuantity()).isEqualByComparingTo("500.00");
        assertThat(harvest.getUnit()).isEqualTo("KG");
        assertThat(harvest.getQualityGrade()).isEqualTo("Grade A");

        CropCycleResponse reloaded = cropService.getCycle(cycle.getId(), farmId);
        assertThat(reloaded.getStatus()).isEqualTo("HARVESTED");
        assertThat(reloaded.getActualHarvestDate()).isEqualTo(LocalDate.now());
    }

    private FieldResponse createField(String name) {
        FieldRequest req = new FieldRequest();
        req.setName(name);
        req.setSizeHectares(new BigDecimal("1.00"));
        return cropService.createField(req, farmId);
    }

    private CropResponse createCrop(String name, int daysToHarvest) {
        CropRequest req = new CropRequest();
        req.setName(name);
        req.setExpectedDaysToHarvest(daysToHarvest);
        return cropService.createCrop(req, farmId);
    }

    private CropCycleResponse createCycle(String fieldName, String cropName, int days) {
        FieldResponse field = createField(fieldName);
        CropResponse crop = createCrop(cropName, days);
        CropCycleRequest req = new CropCycleRequest();
        req.setFieldId(field.getId());
        req.setCropId(crop.getId());
        req.setPlantingDate(LocalDate.now());
        return cropService.createCycle(req, farmId);
    }
}