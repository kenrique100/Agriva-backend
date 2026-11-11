package com.agriva.poultry;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.feeding.calculator.FormulationMode;
import com.agriva.feeding.model.dto.request.*;
import com.agriva.feeding.model.dto.response.IngredientResponse;
import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.feeding.port.ConsumerType;
import com.agriva.feeding.repository.*;
import com.agriva.feeding.service.*;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.poultry.model.dto.request.PoultryBatchRequest;
import com.agriva.poultry.model.dto.request.PoultryHouseRequest;
import com.agriva.poultry.model.dto.response.PoultryHouseResponse;
import com.agriva.poultry.model.enums.PoultrySpecies;
import com.agriva.poultry.repository.PoultryBatchRepository;
import com.agriva.poultry.repository.PoultryHouseRepository;
import com.agriva.poultry.service.PoultryBatchService;
import com.agriva.poultry.service.PoultryHouseService;
import com.agriva.shared.exception.InsufficientFeedStoreException;
import com.agriva.shared.exception.UnregisteredConsumerTypeException;
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
class PoultryFeedConsumptionIT {

    @Autowired FeedConsumptionService consumptionService;
    @Autowired ProductionService productionService;
    @Autowired FormulationService formulationService;
    @Autowired IngredientService ingredientService;
    @Autowired PoultryHouseService houseService;
    @Autowired PoultryBatchService batchService;
    @Autowired AuthService authService;

    @Autowired FeedingEventRepository feedingEventRepo;
    @Autowired FarmFeedStoreRepository farmStoreRepo;
    @Autowired FeedBatchRepository feedBatchRepo;
    @Autowired FormulationLineRepository formulationLineRepo;
    @Autowired FormulationRepository formulationRepo;
    @Autowired IngredientStockRepository stockRepo;
    @Autowired IngredientRepository ingredientRepo;
    @Autowired PoultryHouseRepository houseRepo;
    @Autowired PoultryBatchRepository batchRepo;
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
        batchRepo.deleteAll();
        houseRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("pconsume@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("PConsume Farm " + UUID.randomUUID());
        reg.setName("PConsume Master");
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
        userId = authRepository.findByEmail("pconsume@test.local").orElseThrow().getId();
    }

    @Test
    void feed_poultryHouse_decrementsStoreAndSnapshots() {
        UUID houseId = createHouseWithBatch("Broiler House", 5000L);
        produceBatch("100");

        FeedingRequest req = new FeedingRequest();
        req.setConsumerType(ConsumerType.POULTRY);
        req.setConsumerId(houseId);
        req.setQuantityKg(new BigDecimal("25"));
        req.setFedAt(LocalDate.now());

        var resp = consumptionService.feed(req, farmId, userId);

        assertThat(resp.getId()).isNotNull();
        assertThat(resp.getConsumerType()).isEqualTo(ConsumerType.POULTRY);
        assertThat(resp.getConsumerId()).isEqualTo(houseId);
        assertThat(resp.getConsumerDisplayName()).contains("Broiler House");
        assertThat(resp.getConsumerHeadcount()).isEqualTo(5000L);
        assertThat(resp.getQuantityKg()).isEqualByComparingTo("25");

        assertThat(productionService.getStore(farmId).getQuantityOnHandKg())
                .isEqualByComparingTo("75");
    }

    @Test
    void feed_unregisteredConsumerType_throws() {
        produceBatch("100");

        FeedingRequest req = new FeedingRequest();
        req.setConsumerType(ConsumerType.LIVESTOCK); // no adapter registered
        req.setConsumerId(UUID.randomUUID());
        req.setQuantityKg(new BigDecimal("10"));
        req.setFedAt(LocalDate.now());

        assertThatThrownBy(() -> consumptionService.feed(req, farmId, userId))
                .isInstanceOf(UnregisteredConsumerTypeException.class)
                .hasMessageContaining("LIVESTOCK");
    }

    @Test
    void feed_insufficientStore_throws() {
        UUID houseId = createHouseWithBatch("Small House", 1000L);
        produceBatch("10");

        FeedingRequest req = new FeedingRequest();
        req.setConsumerType(ConsumerType.POULTRY);
        req.setConsumerId(houseId);
        req.setQuantityKg(new BigDecimal("25"));
        req.setFedAt(LocalDate.now());

        assertThatThrownBy(() -> consumptionService.feed(req, farmId, userId))
                .isInstanceOf(InsufficientFeedStoreException.class);

        assertThat(feedingEventRepo.count()).isZero();
    }

    @Test
    void feed_unknownHouse_throws() {
        produceBatch("100");

        FeedingRequest req = new FeedingRequest();
        req.setConsumerType(ConsumerType.POULTRY);
        req.setConsumerId(UUID.randomUUID());
        req.setQuantityKg(new BigDecimal("10"));
        req.setFedAt(LocalDate.now());

        assertThatThrownBy(() -> consumptionService.feed(req, farmId, userId))
                .isInstanceOf(com.agriva.shared.exception.ConsumerNotFoundInDomainException.class);
    }

    /**
     * Every DR-8a consumption test exercises BROILER, so the species is fixed
     * inside the helper. (Previously the parameter was uniform at every call
     * site, which triggered a redundant-parameter warning.)
     */
    private UUID createHouseWithBatch(String name, long count) {
        PoultryHouseRequest hr = new PoultryHouseRequest();
        hr.setName(name);
        PoultryHouseResponse house = houseService.createHouse(hr, farmId, userId);

        PoultryBatchRequest br = new PoultryBatchRequest();
        br.setSpecies(PoultrySpecies.BROILER);
        br.setInitialCount(count);
        br.setArrivalDate(LocalDate.now());
        br.setStage("GROWER");
        batchService.createBatch(house.getId(), br, farmId, userId);

        return house.getId();
    }

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