package com.agriva.feeding;

import com.agriva.TestcontainersConfiguration;
import com.agriva.feeding.model.entity.Ingredient;
import com.agriva.feeding.repository.IngredientRepository;
import com.agriva.feeding.repository.IngredientStockRepository;
import com.agriva.feeding.repository.StagedFeedingIngredientRepository;
import com.agriva.feeding.scheduler.StagedFeedingIngredientValidator;
import com.agriva.feeding.service.IngredientService;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.shared.models.dto.BatchStatusResponse;
import com.agriva.shared.models.dto.BatchUploadResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "agriva.feeding.ingredient.validator.cron=0 0 0 1 1 *",
        "agriva.feeding.ingredient.validator.retry-delay-minutes=0",
        "agriva.feeding.ingredient.validator.max-attempts=4",
        "agriva.feeding.ingredient.consumer.immediate-processing.enabled=false"
})
class IngredientBulkPipelineIT {

    @Autowired IngredientService ingredientService;
    @Autowired StagedFeedingIngredientValidator validator;
    @Autowired StagedFeedingIngredientRepository stagedRepo;
    @Autowired IngredientStockRepository stockRepo;
    @Autowired IngredientRepository ingredientRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired AuthService authService;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        stagedRepo.deleteAll();
        stockRepo.deleteAll();
        ingredientRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("bulk-feed@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("BulkFeed Farm " + UUID.randomUUID());
        reg.setName("BulkFeed Master");
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
        userId = authRepository.findByEmail("bulk-feed@test.local").orElseThrow().getId();
    }

    @Test
    void allValidRows_areQueued_thenPromotedToIngredientTable() throws Exception {
        byte[] xlsx = excel(
                row("Soya beans",   "KG", "500",  "1200", "XAF", "Supplier A"),
                row("Fish Meal",    "KG", "200",  "3500", "XAF", "Supplier B"),
                row("Palm Oil",     "L",  "100",  "900",  "XAF", "Supplier C")
        );

        BatchUploadResponse resp = ingredientService.bulkUpload(
                multipart(xlsx), farmId, userId, "bulk-feed@test.local");

        assertThat(resp.getBatchId()).isNotNull();
        assertThat(resp.getTotalRows()).isEqualTo(3);
        assertThat(resp.getQueued()).isEqualTo(3);
        assertThat(resp.getFailed()).isZero();

        // Wait for all rows to reach staging.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stagedRepo.findByFarmIdAndBatchIdOrderByRowNumberAsc(
                        farmId, resp.getBatchId())).hasSize(3));

        // Drive the validator once — all rows should pass and move.
        validator.processStaged();

        BatchStatusResponse status = ingredientService.bulkStatus(resp.getBatchId(), farmId);
        assertThat(status.getTotal()).isEqualTo(3);
        assertThat(status.getPending()).isZero();
        assertThat(status.getMoved()).isEqualTo(3);
        assertThat(status.getFailed()).isZero();
        assertThat(status.getFailedRows()).isNull();

        List<Ingredient> created = ingredientRepo.findAllByFarmIdLive(farmId);
        assertThat(created).hasSize(3);
        assertThat(created).extracting(Ingredient::getName)
                .containsExactlyInAnyOrder("Soya beans", "Fish Meal", "Palm Oil");
    }

    @Test
    void mixedBatch_parseFailuresAreReported_validRowsArePromoted() throws Exception {
        // 3 fully-populated rows + 2 rows missing the required "unit" column.
        // Missing unit is a *parse-time* failure — the parser rejects the row
        // before Kafka is even touched, so those rows never enter staging.
        // This exercises the exact reporting contract the bulk upload endpoint
        // promises: totalRows / queued / failed counts, per-row errors on the
        // upload response, and empty failedRows on the poll (no async failures).
        byte[] xlsx = excel(
                row("Good 1", "KG", "100", "1500", "XAF", "Sup A"),
                row("Good 2", "KG", "200", "1800", "XAF", "Sup B"),
                row("Good 3", "L",  "50",  "900",  "XAF", "Sup C"),
                row("Bad 1",  "",   "10",  "100",  "XAF", null),   // missing unit
                row("Bad 2",  "",   "20",  "200",  "XAF", null)    // missing unit
        );

        BatchUploadResponse resp = ingredientService.bulkUpload(
                multipart(xlsx), farmId, userId, "bulk-feed@test.local");

        assertThat(resp.getBatchId()).isNotNull();
        assertThat(resp.getTotalRows()).isEqualTo(5);
        assertThat(resp.getQueued()).isEqualTo(3);
        assertThat(resp.getFailed()).isEqualTo(2);
        assertThat(resp.getResults()).hasSize(5);

        assertThat(resp.getResults())
                .filteredOn(r -> "FAILED".equals(r.getStatus()))
                .extracting(BatchUploadResponse.RowResult::getRowNumber)
                .containsExactlyInAnyOrder(4, 5);
        assertThat(resp.getResults())
                .filteredOn(r -> "FAILED".equals(r.getStatus()))
                .allSatisfy(r -> assertThat(r.getError())
                        .contains("Unit is required"));

        // Only the 3 parse-clean rows reach staging.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stagedRepo.findByFarmIdAndBatchIdOrderByRowNumberAsc(
                        farmId, resp.getBatchId())).hasSize(3));

        validator.processStaged();

        BatchStatusResponse status = ingredientService.bulkStatus(resp.getBatchId(), farmId);
        assertThat(status.getTotal()).isEqualTo(3);
        assertThat(status.getPending()).isZero();
        assertThat(status.getMoved()).isEqualTo(3);
        assertThat(status.getFailed()).isZero();
        assertThat(status.getFailedRows()).isNull();

        assertThat(ingredientRepo.findAllByFarmIdLive(farmId)).hasSize(3);
    }

    private MockMultipartFile multipart(byte[] bytes) {
        return new MockMultipartFile(
                "file", "ingredients.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);
    }

    private byte[] excel(String[]... rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Ingredients");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("name");
            header.createCell(1).setCellValue("unit");
            header.createCell(2).setCellValue("quantity");
            header.createCell(3).setCellValue("costperunit");
            header.createCell(4).setCellValue("currency");
            header.createCell(5).setCellValue("supplier");

            for (int i = 0; i < rows.length; i++) {
                Row r = sheet.createRow(i + 1);
                for (int j = 0; j < rows[i].length; j++) {
                    String v = rows[i][j];
                    if (v != null) r.createCell(j).setCellValue(v);
                }
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    private String[] row(String name, String unit, String qty,
                         String cost, String currency, String supplier) {
        return new String[]{name, unit, qty, cost, currency, supplier};
    }
}