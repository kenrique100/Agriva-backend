package com.agriva.farming;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.model.dto.requests.CropRequest;
import com.agriva.farming.model.dto.requests.FieldRequest;
import com.agriva.farming.model.dto.response.CropBulkUploadResponse;
import com.agriva.farming.model.dto.response.CropResponse;
import com.agriva.farming.model.dto.response.FieldResponse;
import com.agriva.farming.repository.*;
import com.agriva.finance.repository.ExpenseRepository;
import com.agriva.finance.repository.IncomeRepository;
import com.agriva.finance.repository.InvestmentRepository;
import com.agriva.finance.repository.TransactionRepository;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.farming.model.entity.CropCycle;
import com.agriva.payroll.repository.SalaryPaymentRepository;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.farming.model.enums.StagedCropCycleStatus;
import com.agriva.personnel.repository.PersonnelRepository;
import com.agriva.personnel.repository.PersonnelStatusHistoryRepository;
import com.agriva.farming.scheduler.StagedCropCycleValidator;
import com.agriva.identity.service.AuthService;
import com.agriva.farming.services.CropManagementService;
import com.agriva.task.repository.TaskRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CropBulkUploadIT {

    @Autowired CropManagementService cropService;
    @Autowired AuthService authService;
    @Autowired StagedCropCycleValidator stagedCropValidator;

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
        reg.setEmail("bulk@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Bulk Farm " + UUID.randomUUID());
        reg.setName("Bulk Master");
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
        userId = authRepository.findByEmail("bulk@test.local").orElseThrow().getId();
    }

    @Test
    void bulkUploadFields_createsAllValidRows() throws Exception {
        MultipartFile file = buildExcel(
                new String[]{"name", "size_hectares", "soil_type"},
                new Object[]{"North Plot", 2.50, "Loamy"},
                new Object[]{"South Plot", 1.50, "Clay"},
                new Object[]{"East Plot",  3.00, "Sandy"}
        );

        CropBulkUploadResponse resp = cropService.bulkUploadFields(file, farmId);

        assertThat(resp.getTotalRows()).isEqualTo(3);
        assertThat(resp.getCreated()).isEqualTo(3);
        assertThat(resp.getFailed()).isZero();

        List<FieldResponse> fields = cropService.getFields(farmId);
        assertThat(fields).hasSize(3);
        assertThat(fields).extracting(FieldResponse::getName)
                .containsExactlyInAnyOrder("North Plot", "South Plot", "East Plot");
    }

    @Test
    void bulkUploadFields_duplicateName_failsThatRowOnly() throws Exception {
        FieldRequest existing = new FieldRequest();
        existing.setName("Existing Field");
        existing.setSizeHectares(new BigDecimal("1.00"));
        cropService.createField(existing, farmId);

        MultipartFile file = buildExcel(
                new String[]{"name", "size_hectares", "soil_type"},
                new Object[]{"New Field", 2.00, "Loamy"},
                new Object[]{"Existing Field", 3.00, "Clay"}
        );

        CropBulkUploadResponse resp = cropService.bulkUploadFields(file, farmId);

        assertThat(resp.getTotalRows()).isEqualTo(2);
        assertThat(resp.getCreated()).isEqualTo(1);
        assertThat(resp.getFailed()).isEqualTo(1);

        var failures = resp.getResults().stream()
                .filter(r -> "FAILED".equals(r.getStatus()))
                .toList();
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0).getError()).contains("already exists");

        assertThat(cropService.getFields(farmId)).hasSize(2);
    }

    @Test
    void bulkUploadCrops_createsAllValidRows() throws Exception {
        MultipartFile file = buildExcel(
                new String[]{"name", "variety", "expected_days_to_harvest"},
                new Object[]{"Maize",  "Hybrid 123", 90},
                new Object[]{"Tomato", "Roma",       75}
        );

        CropBulkUploadResponse resp = cropService.bulkUploadCrops(file, farmId);

        assertThat(resp.getTotalRows()).isEqualTo(2);
        assertThat(resp.getCreated()).isEqualTo(2);
        assertThat(resp.getFailed()).isZero();

        List<CropResponse> crops = cropService.getCrops(farmId);
        assertThat(crops).hasSize(2);
        assertThat(crops).extracting(CropResponse::getName)
                .containsExactlyInAnyOrder("Maize", "Tomato");
    }

    @Test
    void bulkUploadCycles_publishesToKafka_consumerStages_validatorMoves() throws Exception {
        createField("North Plot");
        createCrop("Maize", 90);

        MultipartFile file = buildExcel(
                new String[]{"field_name", "crop_name", "variety", "planting_date", "notes"},
                new Object[]{"North Plot", "Maize", "", "2026-12-01", "First cycle"}
        );

        CropBulkUploadResponse resp = cropService.bulkUploadCycles(
                file, farmId, userId, "bulk@test.local");

        assertThat(resp.getTotalRows()).isEqualTo(1);
        // FIX: bulkUploadCycles only ENQUEUES — actual creation is asynchronous.
        assertThat(resp.getQueued()).isEqualTo(1);
        assertThat(resp.getFailed()).isZero();

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(stagedRepo.findByFarmId(farmId, PageRequest.of(0, 100))
                        .getTotalElements())
                        .isGreaterThanOrEqualTo(1)
        );

        stagedCropValidator.process();

        List<CropCycle> cycles = cycleRepo.findByFarmIdLive(farmId);
        assertThat(cycles).hasSize(1);
        CropCycle cycle = cycles.get(0);
        assertThat(cycle.getField().getName()).isEqualTo("North Plot");
        assertThat(cycle.getCrop().getName()).isEqualTo("Maize");
        assertThat(cycle.getNotes()).isEqualTo("First cycle");
        assertThat(cycle.getPlantingDate()).isEqualTo(LocalDate.of(2026, 12, 1));

        var staged = stagedRepo.findByFarmId(farmId, PageRequest.of(0, 100))
                .getContent().get(0);
        assertThat(staged.getStatus()).isEqualTo(StagedCropCycleStatus.MOVED);
        assertThat(staged.getMovedCycleId()).isNotNull();
        assertThat(staged.getProcessedAt()).isNotNull();
    }

    @Test
    void bulkUploadCycles_unknownField_failsAtParseTime() throws Exception {
        createCrop("Maize", 90);

        MultipartFile file = buildExcel(
                new String[]{"field_name", "crop_name", "variety", "planting_date", "notes"},
                new Object[]{"Missing Field", "Maize", "", "2026-12-01", ""}
        );

        CropBulkUploadResponse resp = cropService.bulkUploadCycles(
                file, farmId, userId, "bulk@test.local");

        assertThat(resp.getTotalRows()).isEqualTo(1);
        assertThat(resp.getCreated()).isZero();
        assertThat(resp.getFailed()).isEqualTo(1);
        assertThat(resp.getResults().get(0).getError()).contains("Field not found");

        assertThat(stagedRepo.findByFarmId(farmId, PageRequest.of(0, 100))
                .getTotalElements()).isZero();
    }

    @Test
    void bulkUploadCycles_unknownCrop_failsAtParseTime() throws Exception {
        createField("North Plot");

        MultipartFile file = buildExcel(
                new String[]{"field_name", "crop_name", "variety", "planting_date", "notes"},
                new Object[]{"North Plot", "Missing Crop", "", "2026-12-01", ""}
        );

        CropBulkUploadResponse resp = cropService.bulkUploadCycles(
                file, farmId, userId, "bulk@test.local");

        assertThat(resp.getFailed()).isEqualTo(1);
        assertThat(resp.getResults().get(0).getError()).contains("Crop not found");
    }

    @Test
    void stagingStats_reflectsCurrentState() throws Exception {
        createField("North Plot");
        createCrop("Maize", 90);

        MultipartFile file = buildExcel(
                new String[]{"field_name", "crop_name", "variety", "planting_date", "notes"},
                new Object[]{"North Plot", "Maize", "", "2026-12-01", ""}
        );

        cropService.bulkUploadCycles(file, farmId, userId, "bulk@test.local");

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(stagedRepo.findByFarmId(farmId, PageRequest.of(0, 100))
                        .getTotalElements())
                        .isGreaterThanOrEqualTo(1)
        );

        var stats = cropService.stagingStats(farmId);
        assertThat(stats).containsKeys("PENDING", "RETRYING", "MOVED", "FAILED");
        long present = stats.get("PENDING") + stats.get("MOVED");
        assertThat(present).isGreaterThanOrEqualTo(1);
    }

    private MultipartFile buildExcel(String[] headers, Object[]... rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Object v = rows[r][c];
                    if (v == null) continue;
                    if (v instanceof Number) {
                        row.createCell(c).setCellValue(((Number) v).doubleValue());
                    } else if (v instanceof LocalDate) {
                        row.createCell(c).setCellValue(v.toString());
                    } else {
                        row.createCell(c).setCellValue(v.toString());
                    }
                }
            }
            wb.write(baos);

            return new MockMultipartFile(
                    "file",
                    "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    baos.toByteArray());
        }
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
}