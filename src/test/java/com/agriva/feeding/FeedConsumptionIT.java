package com.agriva.feeding;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.feeding.calculator.FormulationMode;
import com.agriva.feeding.model.dto.request.*;
import com.agriva.feeding.model.dto.response.*;
import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.feeding.port.ConsumerType;
import com.agriva.feeding.repository.*;
import com.agriva.feeding.service.*;
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
import com.agriva.shared.exception.ConsumerNotFoundInDomainException;
import com.agriva.shared.exception.InsufficientFeedStoreException;
import com.agriva.shared.exception.ResourceNotFoundException;
import com.agriva.shared.exception.UnregisteredConsumerTypeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
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
class FeedConsumptionIT {

    @Autowired FeedConsumptionService consumptionService;
    @Autowired ProductionService productionService;
    @Autowired FormulationService formulationService;
    @Autowired IngredientService ingredientService;
    @Autowired PondService pondService;
    @Autowired AuthService authService;

    @Autowired FeedingEventRepository feedingEventRepo;
    @Autowired FarmFeedStoreRepository farmStoreRepo;
    @Autowired FeedBatchRepository batchRepo;
    @Autowired FormulationLineRepository formulationLineRepo;
    @Autowired FormulationRepository formulationRepo;
    @Autowired IngredientStockRepository stockRepo;
    @Autowired IngredientRepository ingredientRepo;
    @Autowired PondStockRepository pondStockRepo;
    @Autowired PondRepository pondRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        feedingEventRepo.deleteAll();
        batchRepo.deleteAll();
        farmStoreRepo.deleteAll();
        formulationLineRepo.deleteAll();
        formulationRepo.deleteAll();
        stockRepo.deleteAll();
        ingredientRepo.deleteAll();
        pondStockRepo.deleteAll();
        pondRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("consume@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Consumption Test " + UUID.randomUUID());
        reg.setName("Consume Master");
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
        userId = authRepository.findByEmail("consume@test.local").orElseThrow().getId();
    }

    @Test
    void feed_pondWithStoreBalance_decrementsStoreAndSnapshotsConsumer() {
        PondResponse pond = createStockedPond("Pond 1", 5000L, "Tilapia");
        UUID batchId = produceBatch("100");

        FeedingRequest req = requestFor(pond.getId(), "25");
        req.setFeedBatchId(batchId);

        FeedingEventResponse resp = consumptionService.feed(req, farmId, userId);

        assertThat(resp.getId()).isNotNull();
        assertThat(resp.getConsumerType()).isEqualTo(ConsumerType.FISHERY);
        assertThat(resp.getConsumerId()).isEqualTo(pond.getId());
        assertThat(resp.getConsumerDisplayName()).contains("Pond 1");
        assertThat(resp.getConsumerHeadcount()).isEqualTo(5000L);
        assertThat(resp.getQuantityKg()).isEqualByComparingTo("25");
        assertThat(resp.getFeedBatchId()).isEqualTo(batchId);
        assertThat(resp.getFedAt()).isEqualTo(LocalDate.now());

        FarmStoreResponse store = productionService.getStore(farmId);
        assertThat(store.getQuantityOnHandKg()).isEqualByComparingTo("75");
        // 75 kg = 1 full bag + 25 kg remainder
        assertThat(store.getFullBags()).isEqualTo(1);
        assertThat(store.getRemainderKg()).isEqualByComparingTo("25");
    }

    @Test
    void feed_withoutFeedBatchId_succeeds() {
        PondResponse pond = createStockedPond("Pond 1", 1000L, "Tilapia");
        produceBatch("100");

        FeedingRequest req = requestFor(pond.getId(), "10");
        // no feedBatchId

        FeedingEventResponse resp = consumptionService.feed(req, farmId, userId);
        assertThat(resp.getFeedBatchId()).isNull();
        assertThat(productionService.getStore(farmId).getQuantityOnHandKg())
                .isEqualByComparingTo("90");
    }

    @Test
    void feed_exactBalance_leavesStoreAtZero() {
        PondResponse pond = createStockedPond("Pond 1", 1000L, "Tilapia");
        produceBatch("50");

        FeedingRequest req = requestFor(pond.getId(), "50");

        consumptionService.feed(req, farmId, userId);
        assertThat(productionService.getStore(farmId).getQuantityOnHandKg())
                .isEqualByComparingTo("0");
    }

    @Test
    void feed_insufficientStore_throws() {
        PondResponse pond = createStockedPond("Pond 1", 1000L, "Tilapia");
        produceBatch("10");

        FeedingRequest req = requestFor(pond.getId(), "25");

        assertThatThrownBy(() -> consumptionService.feed(req, farmId, userId))
                .isInstanceOf(InsufficientFeedStoreException.class)
                .hasMessageContaining("Insufficient feed in store");

        assertThat(feedingEventRepo.count()).isZero();
        assertThat(productionService.getStore(farmId).getQuantityOnHandKg())
                .isEqualByComparingTo("10"); // untouched
    }

    @Test
    void feed_noStoreYet_throws() {
        PondResponse pond = createStockedPond("Pond 1", 1000L, "Tilapia");
        // no production

        FeedingRequest req = requestFor(pond.getId(), "10");

        assertThatThrownBy(() -> consumptionService.feed(req, farmId, userId))
                .isInstanceOf(InsufficientFeedStoreException.class);
    }

    @Test
    void feed_unregisteredConsumerType_throws() {
        produceBatch("100");

        FeedingRequest req = new FeedingRequest();
        req.setConsumerType(ConsumerType.POULTRY); // no adapter registered
        req.setConsumerId(UUID.randomUUID());
        req.setQuantityKg(new BigDecimal("10"));
        req.setFedAt(LocalDate.now());

        assertThatThrownBy(() -> consumptionService.feed(req, farmId, userId))
                .isInstanceOf(UnregisteredConsumerTypeException.class)
                .hasMessageContaining("POULTRY");
    }

    @Test
    void feed_unknownPond_throws() {
        produceBatch("100");

        FeedingRequest req = new FeedingRequest();
        req.setConsumerType(ConsumerType.FISHERY);
        req.setConsumerId(UUID.randomUUID()); // does not exist
        req.setQuantityKg(new BigDecimal("10"));
        req.setFedAt(LocalDate.now());

        assertThatThrownBy(() -> consumptionService.feed(req, farmId, userId))
                .isInstanceOf(ConsumerNotFoundInDomainException.class);
    }

    @Test
    void feed_foreignFeedBatchId_throws() {
        PondResponse pond = createStockedPond("Pond 1", 1000L, "Tilapia");
        produceBatch("100");
        // Batch ID that does not exist on this farm
        UUID foreignBatchId = UUID.randomUUID();

        FeedingRequest req = requestFor(pond.getId(), "10");
        req.setFeedBatchId(foreignBatchId);

        assertThatThrownBy(() -> consumptionService.feed(req, farmId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listFeedings_filtersByConsumer() {
        PondResponse pondA = createStockedPond("Pond A", 5000L, "Tilapia");
        PondResponse pondB = createStockedPond("Pond B", 3000L, "Catfish");
        produceBatch("200");

        consumptionService.feed(requestFor(pondA.getId(), "10"), farmId, userId);
        consumptionService.feed(requestFor(pondA.getId(), "5"), farmId, userId);
        consumptionService.feed(requestFor(pondB.getId(), "20"), farmId, userId);

        var all = consumptionService.listFeedings(farmId, null, null,
                PageRequest.of(0, 20));
        assertThat(all.getTotalElements()).isEqualTo(3);

        var onlyA = consumptionService.listFeedings(farmId,
                ConsumerType.FISHERY, pondA.getId(), PageRequest.of(0, 20));
        assertThat(onlyA.getTotalElements()).isEqualTo(2);
        assertThat(onlyA.getContent())
                .allMatch(e -> e.getConsumerId().equals(pondA.getId()));

        var byType = consumptionService.listFeedings(farmId,
                ConsumerType.FISHERY, null, PageRequest.of(0, 20));
        assertThat(byType.getTotalElements()).isEqualTo(3);
    }

    @Test
    void listFeedings_consumerIdWithoutConsumerType_throws() {
        assertThatThrownBy(() -> consumptionService.listFeedings(
                farmId, null, UUID.randomUUID(), PageRequest.of(0, 20)))
                .isInstanceOf(com.agriva.shared.exception.BusinessException.class)
                .hasMessageContaining("consumerType");
    }

    @Test
    void concurrentFeedings_justEnoughForOne_onlyOneSucceeds() throws Exception {
        PondResponse pond = createStockedPond("Pond 1", 1000L, "Tilapia");
        produceBatch("100"); // store has 100 kg

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        Callable<Object> task = () -> {
            start.await(10, TimeUnit.SECONDS);
            try {
                return consumptionService.feed(
                        requestFor(pond.getId(), "60"), farmId, userId);
            } catch (InsufficientFeedStoreException ex) {
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
            if (f.get() instanceof FeedingEventResponse) successes++;
            else if (f.get() instanceof InsufficientFeedStoreException) failures++;
        }
        assertThat(successes).isEqualTo(1);
        assertThat(failures).isEqualTo(1);

        assertThat(feedingEventRepo.count()).isEqualTo(1);
        assertThat(productionService.getStore(farmId).getQuantityOnHandKg())
                .isEqualByComparingTo("40"); // 100 - 60
    }

    private PondResponse createStockedPond(String name, long qty, String species) {
        PondRequest p = new PondRequest();
        p.setName(name);
        PondResponse pond = pondService.createPond(p, farmId, userId);

        PondStockRequest s = new PondStockRequest();
        s.setSpecies(species);
        s.setQuantity(qty);
        pondService.addStock(pond.getId(), s, farmId);
        return pond;
    }

    /** Creates enough ingredient, one formulation, and produces the requested kg. */
    private UUID produceBatch(String kg) {
        IngredientRequest ir = new IngredientRequest();
        ir.setName("Maize");
        ir.setUnit(IngredientUnit.KG);
        ir.setCostPerUnit(new BigDecimal("500"));
        IngredientResponse ing = ingredientService.createIngredient(ir, farmId, userId);

        IngredientStockReplenishRequest sr = new IngredientStockReplenishRequest();
        sr.setQuantity(new BigDecimal(kg).add(new BigDecimal("1000")));
        ingredientService.replenishStock(ing.getId(), sr, farmId, userId);

        FormulationRequest fr = new FormulationRequest();
        fr.setName("Batch-source " + UUID.randomUUID());
        fr.setTargetSpecies("TILAPIA");
        fr.setMode(FormulationMode.CUSTOM);
        FormulationRequest.Line line = new FormulationRequest.Line();
        line.setIngredientId(ing.getId());
        line.setQuantityKg(new BigDecimal("100"));
        fr.setLines(List.of(line));
        FormulationResponse formulation = formulationService.createFormulation(
                fr, farmId, userId);

        ProductionRequest pr = new ProductionRequest();
        pr.setFormulationId(formulation.getId());
        pr.setProducedKg(new BigDecimal(kg));
        pr.setProductionDate(LocalDate.now());
        return productionService.produce(pr, farmId, userId).getId();
    }

    private FeedingRequest requestFor(UUID pondId, String kg) {
        FeedingRequest r = new FeedingRequest();
        r.setConsumerType(ConsumerType.FISHERY);
        r.setConsumerId(pondId);
        r.setQuantityKg(new BigDecimal(kg));
        r.setFedAt(LocalDate.now());
        return r;
    }
}