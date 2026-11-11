package com.agriva.feeding;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.feeding.model.enums.StagedFeedingStatus;
import com.agriva.feeding.repository.*;
import com.agriva.feeding.scheduler.*;
import com.agriva.feeding.service.FeedBulkService;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
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
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FeedBulkValidationFailureIT {

    @Autowired FeedBulkService bulkService;
    @Autowired StagedFeedingIngredientValidator ingredientValidator;
    @Autowired StagedFeedingFormulationValidator formulationValidator;
    @Autowired StagedFeedingEventValidator eventValidator;

    @Autowired StagedFeedingIngredientRepository stagedIngredientRepo;
    @Autowired StagedFeedingFormulationRepository stagedFormulationRepo;
    @Autowired StagedFeedingEventRepository stagedEventRepo;
    @Autowired IngredientRepository ingredientRepo;
    @Autowired IngredientStockRepository ingredientStockRepo;
    @Autowired FormulationRepository formulationRepo;
    @Autowired FormulationLineRepository formulationLineRepo;
    @Autowired FeedBatchRepository feedBatchRepo;
    @Autowired FarmFeedStoreRepository farmStoreRepo;
    @Autowired FeedingEventRepository feedingEventRepo;
    @Autowired com.agriva.fishery.repository.PondRepository pondRepo;
    @Autowired com.agriva.fishery.repository.PondStockRepository pondStockRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired AuthService authService;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        feedingEventRepo.deleteAll();
        feedBatchRepo.deleteAll();
        farmStoreRepo.deleteAll();
        formulationLineRepo.deleteAll();
        formulationRepo.deleteAll();
        pondStockRepo.deleteAll();
        pondRepo.deleteAll();
        ingredientStockRepo.deleteAll();
        ingredientRepo.deleteAll();
        stagedEventRepo.deleteAll();
        stagedFormulationRepo.deleteAll();
        stagedIngredientRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("failfeed@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("FailFeed " + UUID.randomUUID());
        reg.setName("FailFeed Master");
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
        userId = authRepository.findByEmail("failfeed@test.local").orElseThrow().getId();
    }

    @Test
    void ingredientRowWithMissingName_failsAtParseTime() throws Exception {
        MultipartFile file = buildExcel(
                new String[]{"name", "unit"},
                new Object[]{"", "KG"} // name is empty → parser fails
        );

        var resp = bulkService.bulkUploadIngredients(file, farmId, userId, "failfeed@test.local");
        assertThat(resp.getTotalRows()).isEqualTo(1);
        assertThat(resp.getQueued()).isZero();
        assertThat(resp.getFailed()).isEqualTo(1);
        assertThat(resp.getResults().get(0).getError()).contains("name is required");

        assertThat(stagedIngredientRepo.findByFarmId(farmId, PageRequest.of(0, 10))
                .getTotalElements()).isZero();
    }

    @Test
    void formulationRowWithUnknownIngredient_retriesThenFails() throws Exception {
        MultipartFile file = buildExcel(
                new String[]{"name", "target_species", "mode", "target_quantity_kg",
                        "ingredient_name", "quantity_kg"},
                new Object[]{"NeverExists", "TILAPIA", "CUSTOM", 100.0, "Missing Ingredient", 50.0}
        );

        bulkService.bulkUploadFormulations(file, farmId, userId, "failfeed@test.local");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stagedFormulationRepo.findByFarmId(farmId, PageRequest.of(0, 10))
                        .getTotalElements()).isGreaterThanOrEqualTo(1));

        // 4 attempts (max-attempts=4) — retry-delay=0 in test profile
        for (int i = 0; i < 4; i++) formulationValidator.processStaged();

        var staged = stagedFormulationRepo.findByFarmId(farmId, PageRequest.of(0, 10))
                .getContent().get(0);
        assertThat(staged.getStatus()).isEqualTo(StagedFeedingStatus.FAILED);
        assertThat(staged.getValidationErrors()).contains("Ingredient not found");
    }

    @Test
    void feedingRowWithUnknownConsumerType_failsImmediately() throws Exception {
        MultipartFile file = buildExcel(
                new String[]{"consumer_type", "consumer_name", "quantity_kg", "fed_at"},
                new Object[]{"POULTRY", "Nonexistent Coop", 10.0, "2026-11-10"}
        );

        bulkService.bulkUploadFeedings(file, farmId, userId, "failfeed@test.local");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stagedEventRepo.findByFarmId(farmId, PageRequest.of(0, 10))
                        .getTotalElements()).isGreaterThanOrEqualTo(1));

        eventValidator.processStaged();

        var staged = stagedEventRepo.findByFarmId(farmId, PageRequest.of(0, 10))
                .getContent().get(0);
        assertThat(staged.getStatus()).isEqualTo(StagedFeedingStatus.FAILED);
        assertThat(staged.getValidationErrors()).contains("POULTRY");
    }

    private MultipartFile buildExcel(String[] headers, Object[]... rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Sheet1");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++)
                header.createCell(i).setCellValue(headers[i]);
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) {
                    Object v = rows[r][c];
                    if (v == null) continue;
                    if (v instanceof Number)
                        row.createCell(c).setCellValue(((Number) v).doubleValue());
                    else
                        row.createCell(c).setCellValue(v.toString());
                }
            }
            wb.write(baos);
            return new MockMultipartFile("file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    baos.toByteArray());
        }
    }
}