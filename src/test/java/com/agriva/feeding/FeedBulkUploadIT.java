package com.agriva.feeding;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.feeding.calculator.FormulationMode;
import com.agriva.feeding.model.dto.request.IngredientRequest;
import com.agriva.feeding.model.dto.request.IngredientStockReplenishRequest;
import com.agriva.feeding.model.dto.response.IngredientResponse;
import com.agriva.feeding.model.entity.FarmFeedStore;
import com.agriva.feeding.model.entity.Formulation;
import com.agriva.feeding.model.entity.FormulationLine;
import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.feeding.repository.*;
import com.agriva.feeding.scheduler.*;
import com.agriva.feeding.service.FeedBulkService;
import com.agriva.feeding.service.IngredientService;
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
import com.agriva.shared.models.enums.StagedStatus;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FeedBulkUploadIT {

    @Autowired FeedBulkService bulkService;
    @Autowired IngredientService ingredientService;
    @Autowired AuthService authService;
    @Autowired PondService pondService;

    @Autowired IngredientRepository ingredientRepo;
    @Autowired IngredientStockRepository ingredientStockRepo;
    @Autowired FormulationRepository formulationRepo;
    @Autowired FormulationLineRepository formulationLineRepo;
    @Autowired FeedBatchRepository feedBatchRepo;
    @Autowired FarmFeedStoreRepository farmStoreRepo;
    @Autowired FeedingEventRepository feedingEventRepo;
    @Autowired StagedFeedingIngredientRepository stagedIngredientRepo;
    @Autowired StagedFeedingFormulationRepository stagedFormulationRepo;
    @Autowired StagedFeedingProductionRepository stagedProductionRepo;
    @Autowired StagedFeedingEventRepository stagedEventRepo;

    @Autowired StagedFeedingIngredientValidator ingredientValidator;
    @Autowired StagedFeedingFormulationValidator formulationValidator;
    @Autowired StagedFeedingProductionValidator productionValidator;
    @Autowired StagedFeedingEventValidator eventValidator;

    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired PondRepository pondRepo;
    @Autowired PondStockRepository pondStockRepo;

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
        stagedProductionRepo.deleteAll();
        stagedFormulationRepo.deleteAll();
        stagedIngredientRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("bulkfeed@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("BulkFeed " + UUID.randomUUID());
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
        userId = authRepository.findByEmail("bulkfeed@test.local").orElseThrow().getId();
    }

    @Test
    void bulkUploadIngredients_createsIngredient() throws Exception {
        MultipartFile file = buildExcel(
                new String[]{"name", "unit", "quantity", "cost_per_unit", "currency", "supplier"},
                new Object[]{"Maize", "KG", 500.0, 500.0, "XAF", "Local"}
        );

        var resp = bulkService.bulkUploadIngredients(file, farmId, userId, "bulkfeed@test.local");
        assertThat(resp.getTotalRows()).isEqualTo(1);
        assertThat(resp.getQueued()).isEqualTo(1);
        assertThat(resp.getFailed()).isZero();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stagedIngredientRepo.findByFarmId(farmId, PageRequest.of(0, 10))
                        .getTotalElements()).isGreaterThanOrEqualTo(1));

        ingredientValidator.processStaged();

        var staged = stagedIngredientRepo.findByFarmId(farmId, PageRequest.of(0, 10))
                .getContent().get(0);
        assertThat(staged.getStatus()).isEqualTo(StagedStatus.MOVED);
        assertThat(staged.getMovedIngredientId()).isNotNull();

        var ingredient = ingredientRepo.findById(staged.getMovedIngredientId()).orElseThrow();
        assertThat(ingredient.getName()).isEqualTo("Maize");
        assertThat(ingredient.getUnit()).isEqualTo(IngredientUnit.KG);
        var stock = ingredientStockRepo
                .findByFarmIdAndIngredientId(farmId, ingredient.getId()).orElseThrow();
        assertThat(stock.getQuantityOnHand()).isEqualByComparingTo("500");
    }

    @Test
    void bulkUploadFormulations_createsFormulation() throws Exception {
        createIngredientDirect("Maize");
        createIngredientDirect("Soya beans");

        MultipartFile file = buildExcel(
                new String[]{"name", "target_species", "mode", "target_quantity_kg",
                        "ingredient_name", "quantity_kg"},
                new Object[]{"Auto Custom", "TILAPIA", "CUSTOM", 100.0, "Maize", 70.0},
                new Object[]{"Auto Custom", "TILAPIA", "CUSTOM", 100.0, "Soya beans", 30.0}
        );

        var resp = bulkService.bulkUploadFormulations(file, farmId, userId, "bulkfeed@test.local");
        assertThat(resp.getQueued()).isEqualTo(1);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stagedFormulationRepo.findByFarmId(farmId, PageRequest.of(0, 10))
                        .getTotalElements()).isGreaterThanOrEqualTo(1));

        formulationValidator.processStaged();

        var staged = stagedFormulationRepo.findByFarmId(farmId, PageRequest.of(0, 10))
                .getContent().get(0);
        assertThat(staged.getStatus()).isEqualTo(StagedStatus.MOVED);
        assertThat(staged.getMovedFormulationId()).isNotNull();
        assertThat(formulationLineRepo.findByFormulationIdOrderByPercentageDesc(
                staged.getMovedFormulationId())).hasSize(2);
    }

    @Test
    void bulkUploadProductions_createsBatch() throws Exception {
        createIngredientDirect("Maize");
        UUID formulationId = createFormulationDirect("Prod-Source", "100");

        MultipartFile file = buildExcel(
                new String[]{"formulation_name", "produced_kg", "production_date", "notes"},
                new Object[]{"Prod-Source", 100.0, "2026-11-10", "Bulk test"}
        );

        var resp = bulkService.bulkUploadProductions(file, farmId, userId, "bulkfeed@test.local");
        assertThat(resp.getQueued()).isEqualTo(1);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stagedProductionRepo.findByFarmId(farmId, PageRequest.of(0, 10))
                        .getTotalElements()).isGreaterThanOrEqualTo(1));

        productionValidator.processStaged();

        var staged = stagedProductionRepo.findByFarmId(farmId, PageRequest.of(0, 10))
                .getContent().get(0);
        assertThat(staged.getStatus()).isEqualTo(StagedStatus.MOVED);
        assertThat(staged.getMovedBatchId()).isNotNull();
        assertThat(feedBatchRepo.findById(staged.getMovedBatchId())).isPresent();
    }

    @Test
    void bulkUploadFeedings_createsFeedingEvent() throws Exception {
        // Produce an ingredient + a formulation + store balance
        createIngredientDirect("Maize");
        createFormulationDirect("Feed-Source", "100");

        FarmFeedStore store = farmStoreRepo.findByFarmId(farmId).orElseGet(() ->
                farmStoreRepo.save(FarmFeedStore.builder()
                        .farmId(farmId)
                        .quantityOnHandKg(BigDecimal.ZERO)
                        .build()));
        store.setQuantityOnHandKg(new BigDecimal("100"));
        farmStoreRepo.save(store);

        PondRequest pr = new PondRequest();
        pr.setName("Bulk Pond");
        PondResponse pond = pondService.createPond(pr, farmId, userId);
        PondStockRequest sr = new PondStockRequest();
        sr.setSpecies("Tilapia");
        sr.setQuantity(1000L);
        pondService.addStock(pond.getId(), sr, farmId);

        MultipartFile file = buildExcel(
                new String[]{"consumer_type", "consumer_name", "quantity_kg", "fed_at", "notes"},
                new Object[]{"FISHERY", "Bulk Pond", 10.0, "2026-11-10", "Bulk feed"}
        );

        var resp = bulkService.bulkUploadFeedings(file, farmId, userId, "bulkfeed@test.local");
        assertThat(resp.getQueued()).isEqualTo(1);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stagedEventRepo.findByFarmId(farmId, PageRequest.of(0, 10))
                        .getTotalElements()).isGreaterThanOrEqualTo(1));

        eventValidator.processStaged();

        var staged = stagedEventRepo.findByFarmId(farmId, PageRequest.of(0, 10))
                .getContent().get(0);
        assertThat(staged.getStatus()).isEqualTo(StagedStatus.MOVED);
        assertThat(staged.getMovedFeedingId()).isNotNull();
        assertThat(feedingEventRepo.findById(staged.getMovedFeedingId())).isPresent();
    }

    private UUID createIngredientDirect(String name) {
        IngredientRequest ir = new IngredientRequest();
        ir.setName(name);
        ir.setUnit(IngredientUnit.KG);
        ir.setCostPerUnit(new BigDecimal("500"));
        IngredientResponse ing = ingredientService.createIngredient(ir, farmId, userId);

        IngredientStockReplenishRequest sr = new IngredientStockReplenishRequest();
        sr.setQuantity(new BigDecimal("1000"));
        ingredientService.replenishStock(ing.getId(), sr, farmId, userId);
        return ing.getId();
    }

    private UUID createFormulationDirect(String name, String qty) {
        var ingredient = ingredientRepo.findByFarmIdAndNameLive(farmId, "Maize").orElseThrow();

        Formulation formulation = formulationRepo.save(Formulation.builder()
                .farmId(farmId)
                .name(name)
                .targetSpecies("TILAPIA")
                .mode(FormulationMode.CUSTOM)
                .targetQuantityKg(new BigDecimal(qty))
                .totalKg(new BigDecimal(qty))
                .totalCost(new BigDecimal("50000"))
                .algorithmVersion("test-v1")
                .build());

        formulationLineRepo.save(FormulationLine.builder()
                .formulationId(formulation.getId())
                .ingredientId(ingredient.getId())
                .ingredientName(ingredient.getName())
                .quantityKg(new BigDecimal(qty))
                .percentage(new BigDecimal("100"))
                .unitCost(new BigDecimal("500"))
                .lineCost(new BigDecimal("50000"))
                .build());

        return formulation.getId();
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
                    } else {
                        row.createCell(c).setCellValue(v.toString());
                    }
                }
            }
            wb.write(baos);
            return new MockMultipartFile(
                    "file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    baos.toByteArray());
        }
    }
}