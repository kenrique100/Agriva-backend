package com.agriva.income;

import com.agriva.TestcontainersConfiguration;
import com.agriva.model.dto.auth.AuthResponse;
import com.agriva.model.dto.auth.MasterRegisterRequest;
import com.agriva.model.dto.income.IncomeRequest;
import com.agriva.model.dto.income.IncomeSubmissionAck;
import com.agriva.model.entity.Income;
import com.agriva.model.entity.StagedIncome;
import com.agriva.model.entity.StagedIncomeStatus;
import com.agriva.repository.AuthRepository;
import com.agriva.repository.FarmRepository;
import com.agriva.repository.IncomeRepository;
import com.agriva.repository.StagedIncomeRepository;
import com.agriva.scheduler.StagedIncomeValidator;
import com.agriva.service.AuthService;
import com.agriva.service.IncomeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class IncomePipelineIT {

    @Autowired IncomeService incomeService;
    @Autowired StagedIncomeValidator validator;
    @Autowired AuthService authService;
    @Autowired IncomeRepository incomeRepo;
    @Autowired StagedIncomeRepository stagedRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;

    @BeforeEach
    void setUp() {
        stagedRepo.deleteAll();
        incomeRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("income@test.local");
        reg.setPassword("MasterPass1");
        reg.setFarmName("Income Test Farm " + UUID.randomUUID());
        AuthResponse master = authService.registerMaster(reg);
        farmId = master.getFarmId();
    }

    @Test
    void submitIncome_flowsThroughKafka_thenValidator_movesToIncomeTable() {
        IncomeRequest req = new IncomeRequest();
        req.setAmount(new BigDecimal("1500.50"));
        req.setCurrency("USD");
        req.setSource("Milk sales");
        req.setCategory("Dairy");
        req.setDescription("Test batch");
        req.setIncomeDate(LocalDate.now().minusDays(1));

        IncomeSubmissionAck ack = incomeService.submitIncome(
                req, farmId, null, "income@test.local");

        assertThat(ack.getStatus()).isEqualTo("QUEUED");
        assertThat(ack.getSubmissionId()).isNotNull();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            StagedIncome staged = stagedRepo.findById(ack.getSubmissionId()).orElse(null);
            assertThat(staged).isNotNull();
            assertThat(staged.getStatus()).isEqualTo(StagedIncomeStatus.PENDING);
        });

        validator.processStagedIncomes();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            StagedIncome staged = stagedRepo.findById(ack.getSubmissionId()).orElseThrow();
            assertThat(staged.getStatus()).isEqualTo(StagedIncomeStatus.MOVED);
            assertThat(staged.getMovedIncomeId()).isNotNull();

            List<Income> incomes = incomeRepo.findAll();
            assertThat(incomes).hasSize(1);
            assertThat(incomes.get(0).getAmount()).isEqualByComparingTo("1500.50");
            assertThat(incomes.get(0).getSource()).isEqualTo("Milk sales");
        });
    }

    @Test
    void invalidIncome_getsRetriedThenFailed() {
        IncomeRequest req = new IncomeRequest();
        req.setAmount(new BigDecimal("-1"));
        req.setCurrency("USD");
        req.setSource("Bad source");
        req.setIncomeDate(LocalDate.now().minusDays(1));

        IncomeSubmissionAck ack = incomeService.submitIncome(
                req, farmId, null, "income@test.local");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stagedRepo.findById(ack.getSubmissionId())).isPresent());

        for (int i = 0; i < 4; i++) {
            validator.processStagedIncomes();
        }

        StagedIncome staged = stagedRepo.findById(ack.getSubmissionId()).orElseThrow();
        assertThat(staged.getStatus()).isEqualTo(StagedIncomeStatus.FAILED);
        assertThat(staged.getRetryCount()).isEqualTo(4);
        assertThat(incomeRepo.countByFarmId(farmId)).isZero();
    }
}