package com.agriva.poultry;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.feeding.calculator.FormulationMode;
import com.agriva.feeding.model.dto.request.FeedingRequest;
import com.agriva.feeding.model.dto.request.FormulationRequest;
import com.agriva.feeding.model.dto.request.IngredientRequest;
import com.agriva.feeding.model.dto.request.IngredientStockReplenishRequest;
import com.agriva.feeding.model.dto.request.ProductionRequest;
import com.agriva.feeding.model.dto.response.IngredientResponse;
import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.feeding.port.ConsumerType;
import com.agriva.feeding.repository.FarmFeedStoreRepository;
import com.agriva.feeding.repository.FeedBatchRepository;
import com.agriva.feeding.repository.FeedingEventRepository;
import com.agriva.feeding.repository.FormulationLineRepository;
import com.agriva.feeding.repository.FormulationRepository;
import com.agriva.feeding.repository.IngredientRepository;
import com.agriva.feeding.repository.IngredientStockRepository;
import com.agriva.feeding.service.FeedConsumptionService;
import com.agriva.feeding.service.FormulationService;
import com.agriva.feeding.service.IngredientService;
import com.agriva.feeding.service.ProductionService;
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
import com.agriva.poultry.model.enums.PoultryBatchStatus;
import com.agriva.poultry.model.enums.PoultrySpecies;
import com.agriva.poultry.repository.PoultryBatchRepository;
import com.agriva.poultry.repository.PoultryHouseRepository;
import com.agriva.poultry.repository.PoultryWeightSampleRepository;
import com.agriva.poultry.service.FcrService;
import com.agriva.poultry.service.PoultryBatchService;
import com.agriva.poultry.service.PoultryHouseService;
import com.agriva.poultry.service.PoultryWeightSampleService;
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

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PoultryFcrIT {

    @Autowired FcrService fcrService;
    @Autowired PoultryBatchService batchService;
    @Autowired PoultryHouseService houseService;
    @Autowired PoultryWeightSampleService sampleService;
    @Autowired AuthService authService;
    @Autowired FeedConsumptionService consumptionService;
    @Autowired ProductionService productionService;
    @Autowired FormulationService formulationService;
    @Autowired IngredientService ingredientService;
    @Autowired PoultryBatchRepository batchRepo;
    @Autowired PoultryHouseRepository houseRepo;
    @Autowired PoultryWeightSampleRepository sampleRepo;
    @Autowired FeedingEventRepository feedingEventRepo;
    @Autowired FeedBatchRepository feedBatchRepo;
    @Autowired FarmFeedStoreRepository farmStoreRepo;
    @Autowired FormulationLineRepository formulationLineRepo;
    @Autowired FormulationRepository formulationRepo;
    @Autowired IngredientStockRepository stockRepo;
    @Autowired IngredientRepository ingredientRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        feedingEventRepo.deleteAll();
        feedBatchRepo.deleteAll();
        farmStoreRepo.deleteAll();
        formulationLineRepo.deleteAll();
        formulationRepo.deleteAll();
        stockRepo.deleteAll();
        ingredientRepo.deleteAll();
        sampleRepo.deleteAll();
        batchRepo.deleteAll();
        houseRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("pfcr@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("PFCR " + UUID.randomUUID());
        reg.setName("PFCR Master");
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
        userId = authRepository.findByEmail("pfcr@test.local").orElseThrow().getId();
    }

    @Test
    void compute_withFeedAndWeightSamples_returnsCorrectFcr() {
        // House + single BROILER batch, 1000 birds, baseline 50g
        UUID houseId = createHouse("FCR House");
        UUID batchId = createBatch(houseId, PoultrySpecies.BROILER, 1000L,
                new BigDecimal("50.00"));

        // Produce 200 kg feed and feed 150 kg to the house
        produceBatch("200");
        feedHouse(houseId, "150");

        // Weight sample: birds now 2000g. Initial biomass stays 50×1000/1000=50 kg.
        WeightSampleRequest ws = new WeightSampleRequest();
        ws.setAverageWeightG(new BigDecimal("2000.00"));
        ws.setSampleSize(50);
        ws.setSampledAt(LocalDate.now());
        sampleService.record(batchId, ws, farmId, userId);

        // Expected:
        //   initialBiomass = 50g × 1000 / 1000 = 50 kg
        //   currentBiomass = 2000g × 1000 / 1000 = 2000 kg
        //   weightGain = 1950 kg
        //   feed = 150 kg
        //   FCR = 150 / 1950 = 0.0769 (SCALE=4, HALF_UP)
        var resp = fcrService.compute(batchId, farmId);

        assertThat(resp.getFcr()).isNotNull();
        assertThat(resp.getFcr()).isEqualByComparingTo("0.0769");
        assertThat(resp.getFeedConsumedKg()).isEqualByComparingTo("150");
        assertThat(resp.getInitialBiomassKg()).isEqualByComparingTo("50");
        assertThat(resp.getCurrentBiomassKg()).isEqualByComparingTo("2000");
        assertThat(resp.getWeightGainKg()).isEqualByComparingTo("1950");
        assertThat(resp.getInitialCount()).isEqualTo(1000L);
        assertThat(resp.getCurrentCount()).isEqualTo(1000L);
        assertThat(resp.getNote()).isNull();
        assertThat(resp.getBatchId()).isEqualTo(batchId);
        assertThat(resp.getHouseId()).isEqualTo(houseId);
    }

    @Test
    void compute_afterMortality_usesCurrentCountForBiomass() {
        UUID houseId = createHouse("FCR Mortality House");
        UUID batchId = createBatch(houseId, PoultrySpecies.BROILER, 1000L,
                new BigDecimal("50.00"));

        // Kill 100 birds — currentCount becomes 900, initialCount stays 1000
        // (mortality is available via DR-8b's PoultryMortalityService, but we
        // don't autowire it here to keep this test focused on FCR math).
        // Instead, we simulate by decrementing via repository directly:
        var b = batchRepo.findById(batchId).orElseThrow();
        b.setCurrentCount(900L);
        batchRepo.save(b);

        produceBatch("200");
        feedHouse(houseId, "100");

        // New sample: birds average 2100g now
        WeightSampleRequest ws = new WeightSampleRequest();
        ws.setAverageWeightG(new BigDecimal("2100.00"));
        ws.setSampleSize(50);
        ws.setSampledAt(LocalDate.now());
        sampleService.record(batchId, ws, farmId, userId);

        // initial biomass = 50g × 1000 / 1000 = 50 kg   ← uses initialCount
        // current biomass = 2100g × 900 / 1000 = 1890 kg ← uses currentCount
        // weightGain = 1840 kg; feed = 100 kg; FCR = 100/1840 = 0.0543
        var resp = fcrService.compute(batchId, farmId);

        assertThat(resp.getFcr()).isEqualByComparingTo("0.0543");
        assertThat(resp.getInitialCount()).isEqualTo(1000L);
        assertThat(resp.getCurrentCount()).isEqualTo(900L);
        assertThat(resp.getInitialBiomassKg()).isEqualByComparingTo("50");
        assertThat(resp.getCurrentBiomassKg()).isEqualByComparingTo("1890");
    }

    @Test
    void compute_noWeightSample_returnsNullWithNote() {
        UUID houseId = createHouse("NoWeight House");
        UUID batchId = createBatch(houseId, PoultrySpecies.BROILER, 1000L, null);

        produceBatch("100");
        feedHouse(houseId, "50");

        var resp = fcrService.compute(batchId, farmId);

        assertThat(resp.getFcr()).isNull();
        assertThat(resp.getWeightGainKg()).isNull();
        assertThat(resp.getNote()).contains("weight data missing");
    }

    @Test
    void compute_multipleActiveBatches_returnsNullWithNote() {
        UUID houseId = createHouse("MultiBatch House");
        UUID batch1 = createBatch(houseId, PoultrySpecies.BROILER, 1000L,
                new BigDecimal("50.00"));
        createBatch(houseId, PoultrySpecies.BROILER, 500L, new BigDecimal("50.00"));

        produceBatch("100");
        feedHouse(houseId, "50");

        WeightSampleRequest ws = new WeightSampleRequest();
        ws.setAverageWeightG(new BigDecimal("2000.00"));
        ws.setSampleSize(50);
        ws.setSampledAt(LocalDate.now());
        sampleService.record(batch1, ws, farmId, userId);

        var resp = fcrService.compute(batch1, farmId);

        assertThat(resp.getFcr()).isNull();
        assertThat(resp.getNote()).contains("multiple active batches");
    }

    @Test
    void compute_closedBatch_returnsNullWithNote() {
        UUID houseId = createHouse("Closed House");
        UUID batchId = createBatch(houseId, PoultrySpecies.BROILER, 1000L,
                new BigDecimal("50.00"));
        batchService.updateStatus(batchId, PoultryBatchStatus.CLOSED, farmId);

        var resp = fcrService.compute(batchId, farmId);

        assertThat(resp.getFcr()).isNull();
        assertThat(resp.getNote()).contains("not ACTIVE");
    }

    @Test
    void compute_noFeedDelivered_returnsNullWithNote() {
        UUID houseId = createHouse("NoFeed House");
        UUID batchId = createBatch(houseId, PoultrySpecies.BROILER, 1000L,
                new BigDecimal("50.00"));

        // Record weight sample but feed nothing
        WeightSampleRequest ws = new WeightSampleRequest();
        ws.setAverageWeightG(new BigDecimal("2000.00"));
        ws.setSampleSize(50);
        ws.setSampledAt(LocalDate.now());
        sampleService.record(batchId, ws, farmId, userId);

        var resp = fcrService.compute(batchId, farmId);

        assertThat(resp.getFcr()).isNull();
        assertThat(resp.getNote()).contains("no feed has been delivered");
    }

    @Test
    void compute_weightGainZero_returnsNullWithNote() {
        UUID houseId = createHouse("ZeroGain House");
        UUID batchId = createBatch(houseId, PoultrySpecies.BROILER, 1000L,
                new BigDecimal("500.00"));

        produceBatch("100");
        feedHouse(houseId, "50");

        // Sample shows the same weight as baseline → zero gain
        WeightSampleRequest ws = new WeightSampleRequest();
        ws.setAverageWeightG(new BigDecimal("500.00"));
        ws.setSampleSize(50);
        ws.setSampledAt(LocalDate.now());
        sampleService.record(batchId, ws, farmId, userId);

        var resp = fcrService.compute(batchId, farmId);

        assertThat(resp.getFcr()).isNull();
        assertThat(resp.getNote()).contains("zero or negative");
    }

    @Test
    void compute_unknownBatch_throws() {
        try {
            fcrService.compute(UUID.randomUUID(), farmId);
            org.junit.jupiter.api.Assertions.fail("expected ResourceNotFoundException");
        } catch (com.agriva.shared.exception.ResourceNotFoundException expected) {
            // ok
        }
    }

    private UUID createHouse(String name) {
        PoultryHouseRequest hr = new PoultryHouseRequest();
        hr.setName(name);
        PoultryHouseResponse house = houseService.createHouse(hr, farmId, userId);
        return house.getId();
    }

    private UUID createBatch(UUID houseId, PoultrySpecies species, long count,
                             BigDecimal avgWeightG) {
        PoultryBatchRequest br = new PoultryBatchRequest();
        br.setSpecies(species);
        br.setInitialCount(count);
        br.setArrivalDate(LocalDate.now().minusDays(7));
        br.setStage("GROWER");
        br.setAverageWeightG(avgWeightG);
        PoultryBatchResponse batch = batchService.createBatch(houseId, br, farmId, userId);
        return batch.getId();
    }

    private void feedHouse(UUID houseId, String kg) {
        FeedingRequest req = new FeedingRequest();
        req.setConsumerType(ConsumerType.POULTRY);
        req.setConsumerId(houseId);
        req.setQuantityKg(new BigDecimal(kg));
        req.setFedAt(LocalDate.now());
        consumptionService.feed(req, farmId, userId);
    }

    /** Produces {@code kg} kg of feed into the farm store so feedHouse() can decrement it. */
    private UUID produceBatch(String kg) {
        IngredientRequest ir = new IngredientRequest();
        ir.setName("Maize " + UUID.randomUUID());
        ir.setUnit(IngredientUnit.KG);
        ir.setCostPerUnit(new BigDecimal("500"));
        IngredientResponse ing = ingredientService.createIngredient(ir, farmId, userId);

        IngredientStockReplenishRequest sr = new IngredientStockReplenishRequest();
        sr.setQuantity(new BigDecimal(kg).add(new BigDecimal("1000")));
        ingredientService.replenishStock(ing.getId(), sr, farmId, userId);

        FormulationRequest fr = new FormulationRequest();
        fr.setName("Feed source " + UUID.randomUUID());
        fr.setTargetSpecies("BROILER");
        fr.setMode(FormulationMode.CUSTOM);
        FormulationRequest.Line line = new FormulationRequest.Line();
        line.setIngredientId(ing.getId());
        line.setQuantityKg(new BigDecimal("100"));
        fr.setLines(List.of(line));
        var formulation = formulationService.createFormulation(fr, farmId, userId);

        ProductionRequest pr = new ProductionRequest();
        pr.setFormulationId(formulation.getId());
        pr.setProducedKg(new BigDecimal(kg));
        pr.setProductionDate(LocalDate.now());
        return productionService.produce(pr, farmId, userId).getId();
    }
}