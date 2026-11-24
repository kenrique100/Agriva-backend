package com.agriva.feeding;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.feeding.calculator.FormulationMode;
import com.agriva.feeding.model.dto.request.FormulationRequest;
import com.agriva.feeding.model.dto.response.FormulationResponse;
import com.agriva.feeding.model.dto.response.IngredientResponse;
import com.agriva.feeding.model.dto.request.IngredientRequest;
import com.agriva.feeding.model.dto.request.IngredientStockReplenishRequest;
import com.agriva.feeding.model.enums.IngredientUnit;
import com.agriva.feeding.repository.*;
import com.agriva.feeding.service.FormulationService;
import com.agriva.feeding.service.IngredientService;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.shared.exception.InsufficientIngredientQuantityException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FeedFormulationIT {

    @Autowired FormulationService formulationService;
    @Autowired IngredientService ingredientService;
    @Autowired AuthService authService;
    @Autowired FormulationLineRepository formulationLineRepo;
    @Autowired FormulationRepository formulationRepo;
    @Autowired IngredientStockRepository stockRepo;
    @Autowired IngredientRepository ingredientRepo;
    @Autowired FormulationTemplateRepository templateRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;
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
        reg.setEmail("formula@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Formulation Test " + UUID.randomUUID());
        reg.setName("Formula Master");
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
        userId = authRepository.findByEmail("formula@test.local").orElseThrow().getId();
    }

    @Test
    void customFormulation_persistsLinesAndComputesPercentages() {
        UUID maize = replenish("Maize", "1000", IngredientUnit.KG, "500", null);
        UUID soya = replenish("Soya beans", "1000", IngredientUnit.KG, "800", null);

        FormulationRequest req = customRequest("Test Custom", "TILAPIA",
                line(maize, "70"), line(soya, "30"));

        FormulationResponse resp = formulationService.createFormulation(req, farmId, userId);

        assertThat(resp.getId()).isNotNull();
        assertThat(resp.getMode()).isEqualTo(FormulationMode.CUSTOM);
        assertThat(resp.getTotalKg()).isEqualByComparingTo("100");
        assertThat(resp.getAlgorithmVersion()).isEqualTo("agriva-default-v1");
        assertThat(resp.getLines()).hasSize(2);

        FormulationResponse.Line maizeLine = resp.getLines().stream()
                .filter(l -> l.getIngredientId().equals(maize)).findFirst().orElseThrow();
        assertThat(maizeLine.getPercentage()).isEqualByComparingTo("70");
        assertThat(maizeLine.getLineCost()).isEqualByComparingTo("35000");
    }

    @Test
    void customFormulation_insufficientStock_throws() {
        UUID maize = replenish("Maize", "10", IngredientUnit.KG, "500", null);

        FormulationRequest req = customRequest("Too much", "TILAPIA",
                line(maize, "100"));

        assertThatThrownBy(() -> formulationService.createFormulation(req, farmId, userId))
                .isInstanceOf(InsufficientIngredientQuantityException.class);
    }

    @Test
    void customFormulation_unknownIngredient_throws() {
        FormulationRequest req = customRequest("Bad", "TILAPIA",
                line(UUID.randomUUID(), "10"));

        assertThatThrownBy(() -> formulationService.createFormulation(req, farmId, userId))
                .isInstanceOf(com.agriva.shared.exception.ResourceNotFoundException.class);
    }

    @Test
    void customFormulation_missingLines_throws() {
        FormulationRequest req = new FormulationRequest();
        req.setName("No lines");
        req.setTargetSpecies("TILAPIA");
        req.setMode(FormulationMode.CUSTOM);

        assertThatThrownBy(() -> formulationService.createFormulation(req, farmId, userId))
                .isInstanceOf(com.agriva.shared.exception.BusinessException.class)
                .hasMessageContaining("lines[]");
    }

    @Test
    void automaticFormulation_seedsDefaultTemplate_usesAllIngredients() {
        String[] names = {
                "Soya beans", "Groundnuts", "Blood Meal", "Fish Meal", "Maize",
                "Cassava", "Diphosphate Calcium", "Bone Meal", "Marine Shell Flour",
                "Salt", "Vitamin C", "Premix", "Palm Oil", "Anti-toxin", "Wheat Bran"
        };
        for (String n : names) {
            IngredientUnit unit = "Palm Oil".equals(n) ? IngredientUnit.L : IngredientUnit.KG;
            BigDecimal density = "Palm Oil".equals(n) ? new BigDecimal("0.90") : null;
            replenish(n, "1000", unit, "100", density);
        }

        FormulationRequest req = new FormulationRequest();
        req.setName("Auto Tilapia 350kg");
        req.setTargetSpecies("TILAPIA");
        req.setMode(FormulationMode.ALGORITHM);
        req.setTargetQuantityKg(new BigDecimal("350"));

        FormulationResponse resp = formulationService.createFormulation(req, farmId, userId);

        assertThat(resp.getMode()).isEqualTo(FormulationMode.ALGORITHM);
        assertThat(resp.getTotalKg()).isEqualByComparingTo("350.0000");
        assertThat(resp.getLines()).hasSize(15);
        assertThat(resp.getNutrientSummary()).containsKey("crudeProteinPct");

        FormulationResponse.Line maizeLine = resp.getLines().stream()
                .filter(l -> "Maize".equals(l.getIngredientName())).findFirst().orElseThrow();
        assertThat(maizeLine.getQuantityInStockUnit()).isEqualByComparingTo("70");

        FormulationResponse.Line palmOilLine = resp.getLines().stream()
                .filter(l -> "Palm Oil".equals(l.getIngredientName())).findFirst().orElseThrow();
        assertThat(palmOilLine.getQuantityInStockUnit()).isEqualByComparingTo("7.7778");
    }

    @Test
    void automaticFormulation_missingSeedIngredient_throwsWithIngredientName() {
        replenish("Maize", "1000", IngredientUnit.KG, "500", null);

        FormulationRequest req = new FormulationRequest();
        req.setName("Incomplete");
        req.setTargetSpecies("TILAPIA");
        req.setMode(FormulationMode.ALGORITHM);
        req.setTargetQuantityKg(new BigDecimal("100"));

        assertThatThrownBy(() -> formulationService.createFormulation(req, farmId, userId))
                .isInstanceOf(InsufficientIngredientQuantityException.class)
                .hasMessageContaining("Insufficient ingredient");
    }

    @Test
    void automaticFormulation_missingTargetKg_throws() {
        FormulationRequest req = new FormulationRequest();
        req.setName("No target");
        req.setTargetSpecies("TILAPIA");
        req.setMode(FormulationMode.ALGORITHM);

        assertThatThrownBy(() -> formulationService.createFormulation(req, farmId, userId))
                .isInstanceOf(com.agriva.shared.exception.BusinessException.class)
                .hasMessageContaining("targetQuantityKg");
    }

    @Test
    void listAndGet_roundTrip() {
        UUID maize = replenish("Maize", "1000", IngredientUnit.KG, "500", null);
        FormulationResponse created = formulationService.createFormulation(
                customRequest("List test", "TILAPIA", line(maize, "50")), farmId, userId);

        var page = formulationService.listFormulations(farmId, PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(1);

        FormulationResponse reloaded = formulationService.getFormulation(created.getId(), farmId);
        assertThat(reloaded.getLines()).hasSize(1);
        assertThat(reloaded.getLines().get(0).getIngredientName()).isEqualTo("Maize");
    }

    @Test
    void deleteFormulation_softDeletes() {
        UUID maize = replenish("Maize", "1000", IngredientUnit.KG, "500", null);
        FormulationResponse created = formulationService.createFormulation(
                customRequest("Delete test", "TILAPIA", line(maize, "10")), farmId, userId);

        formulationService.deleteFormulation(created.getId(), farmId, userId);

        assertThatThrownBy(() -> formulationService.getFormulation(created.getId(), farmId))
                .isInstanceOf(com.agriva.shared.exception.ResourceNotFoundException.class);
    }

    private UUID replenish(String name, String qty, IngredientUnit unit,
                           String cost, BigDecimal density) {
        IngredientRequest ir = new IngredientRequest();
        ir.setName(name);
        ir.setUnit(unit);
        if (cost != null) ir.setCostPerUnit(new BigDecimal(cost));
        if (density != null) ir.setDensityKgPerL(density);
        IngredientResponse ing = ingredientService.createIngredient(ir, farmId, userId);

        IngredientStockReplenishRequest sr = new IngredientStockReplenishRequest();
        sr.setQuantity(new BigDecimal(qty));
        ingredientService.replenishStock(ing.getId(), sr, farmId, userId);
        return ing.getId();
    }

    private FormulationRequest customRequest(String name, String species,
                                             FormulationRequest.Line... lines) {
        FormulationRequest req = new FormulationRequest();
        req.setName(name);
        req.setTargetSpecies(species);
        req.setMode(FormulationMode.CUSTOM);
        req.setLines(List.of(lines));
        return req;
    }

    private FormulationRequest.Line line(UUID ingredientId, String qty) {
        FormulationRequest.Line l = new FormulationRequest.Line();
        l.setIngredientId(ingredientId);
        l.setQuantityKg(new BigDecimal(qty));
        return l;
    }
}