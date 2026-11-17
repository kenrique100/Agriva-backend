package com.agriva.finance;

import com.agriva.TestcontainersConfiguration;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.finance.model.dto.request.IncomeRequest;
import com.agriva.finance.model.dto.response.IncomeSubmissionAck;
import com.agriva.shared.models.enums.Currency;
import com.agriva.finance.model.entity.Income;
import com.agriva.finance.model.enums.IncomeCategory;
import com.agriva.finance.model.entity.StagedIncome;
import com.agriva.finance.model.enums.StagedStatus;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.finance.repository.IncomeRepository;
import com.agriva.finance.repository.StagedIncomeRepository;
import com.agriva.finance.scheduler.StagedIncomeValidator;
import com.agriva.identity.service.AuthService;
import com.agriva.finance.service.IncomeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

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
@TestPropertySource(properties = {
        "agriva.income.validator.retry-delay-minutes=0",
        "agriva.income.validator.max-attempts=4"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Income Test Farm " + UUID.randomUUID());
        reg.setName("Income Master");
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
    }

    @Test
    void submitIncome_flowsThroughKafka_thenValidator_movesToIncomeTable() {
        IncomeRequest req = new IncomeRequest();
        req.setAmountReceived(new BigDecimal("1500.50"));
        req.setExpectedAmount(new BigDecimal("1500.50"));
        req.setQuantity(1);
        req.setCurrency(Currency.USD);
        req.setReason("Maize sales");
        req.setCategory(IncomeCategory.CROP_SALES);
        req.setIncomeDate(LocalDate.now().minusDays(1));
        req.setReceipt("REC-001");

        IncomeSubmissionAck ack = incomeService.submitIncome(
                req, farmId, null, "income@test.local");

        assertThat(ack.getStatus()).isEqualTo("QUEUED");
        assertThat(ack.getSubmissionId()).isNotNull();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            StagedIncome staged = stagedRepo.findByIdOnly(ack.getSubmissionId()).orElse(null);
            assertThat(staged).isNotNull();
            assertThat(staged.getStatus()).isEqualTo(StagedStatus.PENDING);
        });

        validator.processStagedIncomes();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            StagedIncome staged = stagedRepo.findByIdOnly(ack.getSubmissionId()).orElseThrow();
            assertThat(staged.getStatus()).isEqualTo(StagedStatus.MOVED);
            assertThat(staged.getMovedIncomeId()).isNotNull();

            List<Income> incomes = incomeRepo.findAll();
            assertThat(incomes).hasSize(1);
            assertThat(incomes.get(0).getAmountReceived()).isEqualByComparingTo("1500.50");
            assertThat(incomes.get(0).getReason()).isEqualTo("Maize sales");
        });
    }

    @Test
    void invalidIncome_getsRetriedThenFailed() {
        IncomeRequest req = new IncomeRequest();
        req.setAmountReceived(new BigDecimal("-1"));
        req.setExpectedAmount(new BigDecimal("100.00"));
        req.setQuantity(1);
        req.setCurrency(Currency.USD);
        req.setReason("Bad source");
        req.setCategory(IncomeCategory.MISCELLANEOUS);
        req.setIncomeDate(LocalDate.now().minusDays(1));

        IncomeSubmissionAck ack = incomeService.submitIncome(
                req, farmId, null, "income@test.local");
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            validator.processStagedIncomes();
            StagedIncome staged = stagedRepo.findByIdOnly(ack.getSubmissionId()).orElse(null);
            assertThat(staged).isNotNull();
            assertThat(staged.getRetryCount()).isGreaterThanOrEqualTo(1);
        });

        for (int i = 0; i < 3; i++) {
            validator.processStagedIncomes();
        }

        StagedIncome staged = stagedRepo.findByIdOnly(ack.getSubmissionId()).orElseThrow();
        assertThat(staged.getStatus()).isEqualTo(StagedStatus.FAILED);
        assertThat(staged.getRetryCount()).isEqualTo(4);
        assertThat(incomeRepo.countByFarmId(farmId)).isZero();
    }
}