package com.agriva.feeding;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.feeding.model.dto.request.IngredientRequest;
import com.agriva.feeding.model.dto.request.IngredientStockReplenishRequest;
import com.agriva.feeding.model.dto.response.IngredientResponse;
import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.feeding.repository.FarmFeedStoreRepository;
import com.agriva.feeding.repository.FeedBatchRepository;
import com.agriva.feeding.repository.FormulationLineRepository;
import com.agriva.feeding.repository.FormulationRepository;
import com.agriva.feeding.repository.IngredientRepository;
import com.agriva.feeding.repository.IngredientStockRepository;
import com.agriva.feeding.repository.FormulationTemplateLineRepository;
import com.agriva.feeding.repository.FormulationTemplateRepository;
import com.agriva.feeding.service.IngredientService;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FeedIngredientCRUDIT {

    @Autowired IngredientService ingredientService;
    @Autowired AuthService authService;
    @Autowired IngredientRepository ingredientRepo;
    @Autowired IngredientStockRepository stockRepo;
    @Autowired FormulationTemplateRepository templateRepo;
    @Autowired FormulationTemplateLineRepository templateLineRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired FormulationLineRepository formulationLineRepo;
    @Autowired FormulationRepository formulationRepo;
    @Autowired FeedBatchRepository feedBatchRepo;
    @Autowired FarmFeedStoreRepository farmStoreRepo;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        feedBatchRepo.deleteAll();
        farmStoreRepo.deleteAll();
        formulationLineRepo.deleteAll();
        formulationRepo.deleteAll();
        stockRepo.deleteAll();
        ingredientRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("ingredients@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Ingredient Test " + UUID.randomUUID());
        reg.setName("Ing Master");
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
        userId = authRepository.findByEmail("ingredients@test.local").orElseThrow().getId();
    }

    @Test
    void createIngredient_persistsAndReturns() {
        IngredientResponse created = ingredientService.createIngredient(
                ingredientReq("Maize", IngredientUnit.KG, "Local supplier", "500.00"),
                farmId, userId);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getFarmId()).isEqualTo(farmId);
        assertThat(created.getName()).isEqualTo("Maize");
        assertThat(created.getUnit()).isEqualTo(IngredientUnit.KG);
        assertThat(created.getCurrentStock()).isNull();
    }

    @Test
    void duplicateIngredientName_rejected() {
        ingredientService.createIngredient(
                ingredientReq("Soya", IngredientUnit.KG, null, null), farmId, userId);

        assertThatThrownBy(() -> ingredientService.createIngredient(
                ingredientReq("Soya", IngredientUnit.KG, null, null), farmId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void listIngredients_returnsFarmScopedOnly() {
        ingredientService.createIngredient(
                ingredientReq("Maize", IngredientUnit.KG, null, null), farmId, userId);
        ingredientService.createIngredient(
                ingredientReq("Soya", IngredientUnit.KG, null, null), farmId, userId);

        var page = ingredientService.listIngredients(farmId, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void replenishStock_createsThenIncrements() {
        IngredientResponse ing = ingredientService.createIngredient(
                ingredientReq("Maize", IngredientUnit.KG, null, null), farmId, userId);

        IngredientStockReplenishRequest first = new IngredientStockReplenishRequest();
        first.setQuantity(new BigDecimal("500.00"));
        IngredientResponse afterFirst = ingredientService.replenishStock(
                ing.getId(), first, farmId, userId);
        assertThat(afterFirst.getCurrentStock().getQuantityOnHand())
                .isEqualByComparingTo("500.00");

        IngredientStockReplenishRequest second = new IngredientStockReplenishRequest();
        second.setQuantity(new BigDecimal("250.00"));
        IngredientResponse afterSecond = ingredientService.replenishStock(
                ing.getId(), second, farmId, userId);
        assertThat(afterSecond.getCurrentStock().getQuantityOnHand())
                .isEqualByComparingTo("750.00");
    }

    @Test
    void systemTemplateSeed_isSumTo100() {
        var systemTemplates = templateRepo.findSystemTemplatesLive();
        assertThat(systemTemplates).isNotEmpty();
        var template = systemTemplates.stream()
                .filter(t -> "AGRIVA_DEFAULT_TILAPIA_GROWER".equals(t.getName()))
                .findFirst()
                .orElseThrow();

        BigDecimal sum = templateLineRepo.sumPercentageByTemplateId(template.getId());
        assertThat(sum).isEqualByComparingTo("100.00");
        assertThat(templateLineRepo.findByTemplateId(template.getId())).hasSize(15);
    }

    private IngredientRequest ingredientReq(String name, IngredientUnit unit,
                                            String supplier, String cost) {
        IngredientRequest r = new IngredientRequest();
        r.setName(name);
        r.setUnit(unit);
        r.setSupplier(supplier);
        if (cost != null) r.setCostPerUnit(new BigDecimal(cost));
        return r;
    }
}