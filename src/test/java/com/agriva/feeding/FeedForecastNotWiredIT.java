package com.agriva.feeding;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.feeding.calculator.ManualDemandForecaster;
import com.agriva.feeding.model.dto.request.DemandForecastRequest;
import com.agriva.feeding.repository.DemandForecastRepository;
import com.agriva.feeding.repository.DemandForecastStageRepository;
import com.agriva.feeding.repository.FarmFeedStoreRepository;
import com.agriva.feeding.repository.FeedBatchRepository;
import com.agriva.feeding.repository.FeedingEventRepository;
import com.agriva.feeding.repository.FormulationLineRepository;
import com.agriva.feeding.repository.FormulationRepository;
import com.agriva.feeding.repository.IngredientRepository;
import com.agriva.feeding.repository.IngredientStockRepository;
import com.agriva.feeding.service.ForecastService;
import com.agriva.fishery.repository.PondRepository;
import com.agriva.fishery.repository.PondStockRepository;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that WITHOUT a wired-in FeedDemandForecaster, POST /feed/forecasts
 * returns 501 (UnsupportedOperationException).
 * <p>
 * This class runs on the shared Spring test context (same Postgres container
 * as FeedConsumptionIT / FeedFormulationIT / FeedProductionIT / Fishery*), so
 * its {@code @BeforeEach} must clean every feeding + fishery table in
 * FK-dependency order — otherwise {@code farmRepository.deleteAll()} is blocked
 * by the ON DELETE RESTRICT FK from {@code formulation_line} to
 * {@code ingredient}, producing the "could not execute batch" failure.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FeedForecastNotWiredIT {

    @Autowired ForecastService forecastService;
    @Autowired AuthService authService;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired ManualDemandForecaster manualForecaster;

    @Autowired FeedingEventRepository feedingEventRepo;
    @Autowired FeedBatchRepository feedBatchRepo;
    @Autowired FarmFeedStoreRepository farmFeedStoreRepo;
    @Autowired FormulationLineRepository formulationLineRepo;
    @Autowired FormulationRepository formulationRepo;
    @Autowired DemandForecastStageRepository demandForecastStageRepo;
    @Autowired DemandForecastRepository demandForecastRepo;
    @Autowired IngredientStockRepository ingredientStockRepo;
    @Autowired IngredientRepository ingredientRepo;

    @Autowired PondStockRepository pondStockRepo;
    @Autowired PondRepository pondRepo;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        feedingEventRepo.deleteAll();
        feedBatchRepo.deleteAll();
        farmFeedStoreRepo.deleteAll();
        formulationLineRepo.deleteAll();
        formulationRepo.deleteAll();
        demandForecastStageRepo.deleteAll();
        demandForecastRepo.deleteAll();
        pondStockRepo.deleteAll();
        pondRepo.deleteAll();
        ingredientStockRepo.deleteAll();
        ingredientRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("notwired@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("NotWired " + UUID.randomUUID());
        reg.setName("NW Master");
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
        AuthResponse m = authService.registerMaster(reg);
        farmId = m.getFarmId();
        userId = authRepository.findByEmail("notwired@test.local").orElseThrow().getId();
    }

    @Test
    void postForecast_withoutCalculator_throwsUnsupportedOperation() {
        DemandForecastRequest req = new DemandForecastRequest();
        req.setSpecies("TILAPIA");
        req.setNumberOfHead(100L);
        req.setInitialWeightKg(new BigDecimal("0.05"));
        req.setTargetWeightKg(new BigDecimal("0.50"));

        assertThatThrownBy(() -> forecastService.createForecast(req, farmId, userId))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("FeedDemandForecaster is not yet wired in");
    }
}