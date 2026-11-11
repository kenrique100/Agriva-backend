package com.agriva.feeding;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.feeding.calculator.FormulationMode;
import com.agriva.feeding.model.dto.request.*;
import com.agriva.feeding.model.dto.response.*;
import com.agriva.feeding.model.entity.FeedBatch;
import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.feeding.repository.*;
import com.agriva.feeding.service.FormulationService;
import com.agriva.feeding.service.IngredientService;
import com.agriva.feeding.service.ProductionService;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.shared.exception.InsufficientIngredientQuantityException;
import com.agriva.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
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
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FeedProductionIT {

    @Autowired ProductionService productionService;
    @Autowired FormulationService formulationService;
    @Autowired IngredientService ingredientService;
    @Autowired AuthService authService;
    @Autowired FeedBatchRepository batchRepo;
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
        cleanDatabase();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("prod@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Production Test " + UUID.randomUUID());
        reg.setName("Prod Master");
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
        userId = authRepository.findByEmail("prod@test.local").orElseThrow().getId();
    }

    /**
     * Leaving rows behind breaks the next test class's setUp(), which does
     * farmRepository.deleteAll() and hits fk_formulation_line_ingredient
     * (ON DELETE RESTRICT). Clean up after ourselves.
     */
    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        batchRepo.deleteAll();
        farmStoreRepo.deleteAll();
        formulationLineRepo.deleteAll();
        formulationRepo.deleteAll();
        stockRepo.deleteAll();
        ingredientRepo.deleteAll();
        farmRepository.deleteAll();
        authRepository.deleteAll();
    }

    @Test
    void produce_exactTarget_createsBagsAndDeductsStock() {
        UUID maize = replenish("Maize", "1000", "500");
        UUID soya  = replenish("Soya",  "1000", "800");
        FormulationResponse formulation = customFormulation("Exact", maize, "70", soya, "30");

        FeedBatchResponse batch = produce(formulation.getId(), "100", LocalDate.now());

        assertThat(batch.getProducedKg()).isEqualByComparingTo("100");
        assertThat(batch.getBagSizeKg()).isEqualByComparingTo("50");
        assertThat(batch.getNumberOfBags()).isEqualTo(2);
        assertThat(batch.getBulkRemainderKg()).isEqualByComparingTo("0");
        // cost = 70×500 + 30×800 = 35000 + 24000 = 59000
        assertThat(batch.getCostTotal()).isEqualByComparingTo("59000");

        assertThat(currentStock(maize)).isEqualByComparingTo("930"); // 1000 - 70
        assertThat(currentStock(soya)).isEqualByComparingTo("970");  // 1000 - 30

        FarmStoreResponse store = productionService.getStore(farmId);
        assertThat(store.getQuantityOnHandKg()).isEqualByComparingTo("100");
        assertThat(store.getFullBags()).isEqualTo(2);
        assertThat(store.getRemainderKg()).isEqualByComparingTo("0");
    }

    @Test
    void produce_smallQuantity_bagMathHandlesRemainder() {
        UUID maize = replenish("Maize", "1000", "500");
        FormulationResponse formulation = customFormulation("Small", maize, "100");

        FeedBatchResponse batch = produce(formulation.getId(), "47", LocalDate.now());

        assertThat(batch.getNumberOfBags()).isZero();
        assertThat(batch.getBulkRemainderKg()).isEqualByComparingTo("47");

        FarmStoreResponse store = productionService.getStore(farmId);
        assertThat(store.getFullBags()).isZero();
        assertThat(store.getRemainderKg()).isEqualByComparingTo("47");
    }

    @Test
    void produce_moreThanTarget_scalesIngredients() {
        UUID maize = replenish("Maize", "1000", "500");
        FormulationResponse formulation = customFormulation("More", maize, "100");

        FeedBatchResponse batch = produce(formulation.getId(), "200", LocalDate.now());

        // scaleFactor = 200/100 = 2.0 → 200 kg of maize consumed
        assertThat(batch.getProducedKg()).isEqualByComparingTo("200");
        assertThat(currentStock(maize)).isEqualByComparingTo("800");

        FarmStoreResponse store = productionService.getStore(farmId);
        assertThat(store.getQuantityOnHandKg()).isEqualByComparingTo("200");
        assertThat(store.getFullBags()).isEqualTo(4);
        assertThat(store.getRemainderKg()).isEqualByComparingTo("0");
    }

    /**
     * Formulation creation validates stock, so we give exactly enough for the
     * formulation (100 kg maize) and then ask produce() for 2× that (200 kg).
     * The produce call must fail with insufficient stock and leave everything
     * untouched.
     */
    @Test
    void produce_insufficientStock_throwsAndDoesNotPersist() {
        UUID maize = replenish("Maize", "100", "500");
        FormulationResponse formulation = customFormulation("Ok", maize, "100");

        // 2× scale → needs 200 kg maize, only 100 kg available.
        assertThatThrownBy(() -> produce(formulation.getId(), "200", LocalDate.now()))
                .isInstanceOf(InsufficientIngredientQuantityException.class)
                .hasMessageContaining("Maize");

        assertThat(batchRepo.count()).isZero();
        assertThat(farmStoreRepo.count()).isZero();
        assertThat(currentStock(maize)).isEqualByComparingTo("100"); // untouched
    }

    @Test
    void produce_formulationNotOnFarm_throws() {
        FormulationResponse formulation = customFormulationOfOtherFarm();

        assertThatThrownBy(() -> produce(formulation.getId(), "10", LocalDate.now()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void multipleProductions_storeBalanceEqualsSumOfBatches() {
        UUID maize = replenish("Maize", "5000", "500");
        FormulationResponse formulation = customFormulation("Multi", maize, "100");

        produce(formulation.getId(), "100", LocalDate.now());
        produce(formulation.getId(), "50", LocalDate.now());
        produce(formulation.getId(), "47", LocalDate.now());

        FarmStoreResponse store = productionService.getStore(farmId);
        assertThat(store.getQuantityOnHandKg()).isEqualByComparingTo("197");
        // 197 kg = 3 bags (150) + 47 remainder
        assertThat(store.getFullBags()).isEqualTo(3);
        assertThat(store.getRemainderKg()).isEqualByComparingTo("47");

        BigDecimal sumOfBatches = batchRepo.findAll().stream()
                .map(FeedBatch::getProducedKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(store.getQuantityOnHandKg()).isEqualByComparingTo(sumOfBatches);
    }

    @Test
    void listBatches_orderedByProductionDateDesc() {
        UUID maize = replenish("Maize", "5000", "500");
        FormulationResponse formulation = customFormulation("List", maize, "100");

        produce(formulation.getId(), "10", LocalDate.now().minusDays(2));
        produce(formulation.getId(), "20", LocalDate.now());

        var page = productionService.listBatches(farmId,
                org.springframework.data.domain.PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent().get(0).getProductionDate())
                .isEqualTo(LocalDate.now());
    }

    @Test
    void concurrentProduction_enoughStock_bothSucceed() throws Exception {
        UUID maize = replenish("Maize", "500", "500");
        FormulationResponse formulation = customFormulation("Concurrent", maize, "100");

        BigDecimal producedPerThread = new BigDecimal("100");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        Callable<FeedBatchResponse> task = () -> {
            start.await(10, TimeUnit.SECONDS);
            try {
                return productionService.produce(
                        requestFor(formulation.getId(), producedPerThread, LocalDate.now()),
                        farmId, userId);
            } finally {
                done.countDown();
            }
        };
        Future<FeedBatchResponse> f1 = pool.submit(task);
        Future<FeedBatchResponse> f2 = pool.submit(task);
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(f1.get().getId()).isNotNull();
        assertThat(f2.get().getId()).isNotNull();

        assertThat(batchRepo.count()).isEqualTo(2);
        assertThat(currentStock(maize)).isEqualByComparingTo("300"); // 500 - 100 - 100
        assertThat(productionService.getStore(farmId).getQuantityOnHandKg())
                .isEqualByComparingTo("200");
    }

    @Test
    void concurrentProduction_justEnoughStockForOne_onlyOneSucceeds() throws Exception {
        UUID maize = replenish("Maize", "100", "500"); // exactly one 100-kg production
        FormulationResponse formulation = customFormulation("Race", maize, "100");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        Callable<Object> task = () -> {
            start.await(10, TimeUnit.SECONDS);
            try {
                return productionService.produce(
                        requestFor(formulation.getId(), new BigDecimal("100"), LocalDate.now()),
                        farmId, userId);
            } catch (InsufficientIngredientQuantityException ex) {
                return ex;
            } finally {
                done.countDown();
            }
        };
        Future<Object> f1 = pool.submit(task);
        Future<Object> f2 = pool.submit(task);
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        long successes = 0, failures = 0;
        for (Future<Object> f : List.of(f1, f2)) {
            if (f.get() instanceof FeedBatchResponse) successes++;
            else if (f.get() instanceof InsufficientIngredientQuantityException) failures++;
        }
        assertThat(successes).isEqualTo(1);
        assertThat(failures).isEqualTo(1);

        assertThat(batchRepo.count()).isEqualTo(1);
        assertThat(currentStock(maize)).isEqualByComparingTo("0");
        assertThat(productionService.getStore(farmId).getQuantityOnHandKg())
                .isEqualByComparingTo("100");
    }

    private UUID replenish(String name, String qty, String cost) {
        IngredientRequest ir = new IngredientRequest();
        ir.setName(name);
        ir.setUnit(IngredientUnit.KG);
        ir.setCostPerUnit(new BigDecimal(cost));
        IngredientResponse ing = ingredientService.createIngredient(ir, farmId, userId);

        IngredientStockReplenishRequest sr = new IngredientStockReplenishRequest();
        sr.setQuantity(new BigDecimal(qty));
        ingredientService.replenishStock(ing.getId(), sr, farmId, userId);
        return ing.getId();
    }

    private FormulationResponse customFormulation(String name, UUID ingredientId, String qty) {
        FormulationRequest req = new FormulationRequest();
        req.setName(name);
        req.setTargetSpecies("TILAPIA");
        req.setMode(FormulationMode.CUSTOM);
        FormulationRequest.Line line = new FormulationRequest.Line();
        line.setIngredientId(ingredientId);
        line.setQuantityKg(new BigDecimal(qty));
        req.setLines(List.of(line));
        return formulationService.createFormulation(req, farmId, userId);
    }

    private FormulationResponse customFormulation(String name, UUID id1, String q1,
                                                  UUID id2, String q2) {
        FormulationRequest req = new FormulationRequest();
        req.setName(name);
        req.setTargetSpecies("TILAPIA");
        req.setMode(FormulationMode.CUSTOM);
        FormulationRequest.Line l1 = new FormulationRequest.Line();
        l1.setIngredientId(id1);
        l1.setQuantityKg(new BigDecimal(q1));
        FormulationRequest.Line l2 = new FormulationRequest.Line();
        l2.setIngredientId(id2);
        l2.setQuantityKg(new BigDecimal(q2));
        req.setLines(List.of(l1, l2));
        return formulationService.createFormulation(req, farmId, userId);
    }

    /**
     * Creates a second farm and formulates against it. Formulation creation
     * validates stock, so we replenish the other farm's ingredient before
     * creating the formulation — otherwise the formulation itself fails and we
     * never reach the produce() call we're trying to test.
     */
    private FormulationResponse customFormulationOfOtherFarm() {
        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("other-" + UUID.randomUUID() + "@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Other Farm " + UUID.randomUUID());
        reg.setName("Other Master");
        reg.setGender(Gender.MALE);
        reg.setDateOfBirth(LocalDate.now().minusYears(35));
        reg.setPhoneNumber("+237600000001");
        reg.setCountry("Cameroon");
        reg.setState("Centre");
        reg.setCity("Yaoundé");
        reg.setAddress("Rue 2");
        reg.setPostalCode("00000");
        reg.setCurrency("XAF");
        reg.setUserAgreement(true);
        AuthResponse other = authService.registerMaster(reg);
        UUID otherFarmId = other.getFarmId();
        UUID otherUserId = authRepository.findByEmail(reg.getEmail()).orElseThrow().getId();

        IngredientRequest ir = new IngredientRequest();
        ir.setName("Maize");
        ir.setUnit(IngredientUnit.KG);
        ir.setCostPerUnit(new BigDecimal("500"));
        IngredientResponse ing = ingredientService.createIngredient(ir, otherFarmId, otherUserId);

        // Give the other farm enough stock so formulation creation validates.
        IngredientStockReplenishRequest sr = new IngredientStockReplenishRequest();
        sr.setQuantity(new BigDecimal("1000"));
        ingredientService.replenishStock(ing.getId(), sr, otherFarmId, otherUserId);

        FormulationRequest req = new FormulationRequest();
        req.setName("Foreign");
        req.setTargetSpecies("TILAPIA");
        req.setMode(FormulationMode.CUSTOM);
        FormulationRequest.Line l = new FormulationRequest.Line();
        l.setIngredientId(ing.getId());
        l.setQuantityKg(new BigDecimal("100"));
        req.setLines(List.of(l));
        return formulationService.createFormulation(req, otherFarmId, otherUserId);
    }

    private FeedBatchResponse produce(UUID formulationId, String kg, LocalDate date) {
        return productionService.produce(
                requestFor(formulationId, new BigDecimal(kg), date), farmId, userId);
    }

    private ProductionRequest requestFor(UUID formulationId, BigDecimal kg, LocalDate date) {
        ProductionRequest r = new ProductionRequest();
        r.setFormulationId(formulationId);
        r.setProducedKg(kg);
        r.setProductionDate(date);
        return r;
    }

    private BigDecimal currentStock(UUID ingredientId) {
        return stockRepo.findByFarmIdAndIngredientId(farmId, ingredientId)
                .map(s -> s.getQuantityOnHand())
                .orElse(BigDecimal.ZERO);
    }
}