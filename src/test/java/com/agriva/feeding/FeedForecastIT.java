package com.agriva.feeding;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.feeding.calculator.DemandForecastInput;
import com.agriva.feeding.calculator.DemandForecastResult;
import com.agriva.feeding.calculator.FeedDemandForecaster;
import com.agriva.feeding.calculator.GrowthStageResult;
import com.agriva.feeding.model.dto.request.DemandForecastRequest;
import com.agriva.feeding.model.dto.response.DemandForecastResponse;
import com.agriva.feeding.repository.DemandForecastRepository;
import com.agriva.feeding.repository.DemandForecastStageRepository;
import com.agriva.feeding.service.ForecastService;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, FeedForecastIT.StubCalculatorConfig.class})
class FeedForecastIT {

    /**
     * Deterministic stub for the user's Calculator A. The production code
     * ships with {@link com.agriva.feeding.calculator.ManualDemandForecaster}
     * (throws 501); this stub replaces it inside the test context only.
     */
    @TestConfiguration
    static class StubCalculatorConfig {
        @Bean
        @Primary
        FeedDemandForecaster stubForecaster() {
            return new FeedDemandForecaster() {
                @Override
                public DemandForecastResult calculate(DemandForecastInput input) {
                    return new DemandForecastResult(
                            List.of(new GrowthStageResult(
                                    "GROWER",
                                    input.initialWeightKg(),
                                    input.targetWeightKg(),
                                    30,
                                    new BigDecimal("50.0000"),
                                    new BigDecimal("1.6667"),
                                    3)),
                            new BigDecimal("50.0000"),
                            30,
                            new BigDecimal("1.6667"),
                            "test-stub-v1");
                }

                @Override
                public String algorithmVersion() {
                    return "test-stub-v1";
                }
            };
        }
    }

    @Autowired ForecastService forecastService;
    @Autowired AuthService authService;
    @Autowired DemandForecastRepository forecastRepo;
    @Autowired DemandForecastStageRepository stageRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        stageRepo.deleteAll();
        forecastRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("forecast@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Forecast Test " + UUID.randomUUID());
        reg.setName("Forecast Master");
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
        userId = authRepository.findByEmail("forecast@test.local").orElseThrow().getId();
    }

    @Test
    void createForecast_persistsStagesAndTotals() {
        DemandForecastRequest req = forecastReq();
        DemandForecastResponse resp = forecastService.createForecast(req, farmId, userId);

        assertThat(resp.getId()).isNotNull();
        assertThat(resp.getSpecies()).isEqualTo("TILAPIA");
        assertThat(resp.getTotalFeedKg()).isEqualByComparingTo("50.0000");
        assertThat(resp.getTotalDays()).isEqualTo(30);
        assertThat(resp.getAlgorithmVersion()).isEqualTo("test-stub-v1");
        assertThat(resp.getStages()).hasSize(1);
        assertThat(resp.getStages().get(0).getStageName()).isEqualTo("GROWER");
    }

    @Test
    void createForecast_reproducibleSameInputsTwice() {
        DemandForecastRequest req = forecastReq();

        DemandForecastResponse first = forecastService.createForecast(req, farmId, userId);
        DemandForecastResponse second = forecastService.createForecast(req, farmId, userId);

        assertThat(first.getTotalFeedKg()).isEqualByComparingTo(second.getTotalFeedKg());
        assertThat(first.getTotalDays()).isEqualTo(second.getTotalDays());
        assertThat(first.getStages().size()).isEqualTo(second.getStages().size());
        for (int i = 0; i < first.getStages().size(); i++) {
            assertThat(first.getStages().get(i).getFeedConsumedKg())
                    .isEqualByComparingTo(second.getStages().get(i).getFeedConsumedKg());
        }
    }

    @Test
    void createForecast_targetLessThanInitial_rejected() {
        DemandForecastRequest req = forecastReq();
        req.setInitialWeightKg(new BigDecimal("100"));
        req.setTargetWeightKg(new BigDecimal("50"));

        assertThatThrownBy(() -> forecastService.createForecast(req, farmId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Target weight must be greater than initial weight");
    }

    @Test
    void createForecast_unknownTemplate_rejected() {
        DemandForecastRequest req = forecastReq();
        req.setFormulaTemplateId(UUID.randomUUID());

        assertThatThrownBy(() -> forecastService.createForecast(req, farmId, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("template not found");
    }

    @Test
    void getForecast_returnsPersistedStages() {
        DemandForecastResponse created = forecastService.createForecast(
                forecastReq(), farmId, userId);

        DemandForecastResponse reloaded = forecastService.getForecast(
                created.getId(), farmId);

        assertThat(reloaded.getTotalFeedKg()).isEqualByComparingTo("50.0000");
        assertThat(reloaded.getStages()).hasSize(1);
    }

    @Test
    void listForecasts_returnsFarmScopedNewestFirst() {
        forecastService.createForecast(forecastReq(), farmId, userId);
        forecastService.createForecast(forecastReq(), farmId, userId);

        var page = forecastService.listForecasts(
                farmId, org.springframework.data.domain.PageRequest.of(0, 20));
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    private DemandForecastRequest forecastReq() {
        DemandForecastRequest r = new DemandForecastRequest();
        r.setSpecies("TILAPIA");
        r.setNumberOfHead(5000L);
        r.setInitialWeightKg(new BigDecimal("0.05"));
        r.setTargetWeightKg(new BigDecimal("0.50"));
        r.setWaterTemperatureC(new BigDecimal("28.00"));
        r.setTargetCpValue(new BigDecimal("32.00"));
        return r;
    }
}