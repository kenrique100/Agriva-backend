package com.agriva.finance;

import com.agriva.TestcontainersConfiguration;
import com.agriva.finance.kafka.producer.ExpenseProducer;
import com.agriva.finance.kafka.producer.IncomeProducer;
import com.agriva.finance.model.dto.request.ExpenseSubmissionEvent;
import com.agriva.finance.model.dto.request.IncomeSubmissionEvent;
import com.agriva.finance.model.dto.request.InvestmentRequest;
import com.agriva.finance.model.entity.StagedExpense;
import com.agriva.finance.model.entity.StagedIncome;
import com.agriva.finance.model.enums.StagedStatus;
import com.agriva.finance.repository.ExpenseRepository;
import com.agriva.finance.repository.IncomeRepository;
import com.agriva.finance.repository.InvestmentRepository;
import com.agriva.finance.repository.StagedExpenseRepository;
import com.agriva.finance.repository.StagedIncomeRepository;
import com.agriva.finance.scheduler.StagedExpenseValidator;
import com.agriva.finance.scheduler.StagedIncomeValidator;
import com.agriva.finance.service.InvestmentService;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.shared.lock.DistributedLockService;
import com.agriva.shared.models.enums.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        // Belt and braces: even if the cron accidentally fired, the test's
        // manual lock is what actually demonstrates the behavior we care
        // about. The cron is disabled so the only concurrent acquirer is
        // ourselves.
        "agriva.expense.validator.cron=0 0 0 1 1 *",
        "agriva.income.validator.cron=0 0 0 1 1 *",
        "agriva.expense.validator.retry-delay-minutes=0",
        "agriva.income.validator.retry-delay-minutes=0"
})
class StagedValidatorLockIT {

    private static final String EXPENSE_LOCK_KEY = "staged-expense-validator";
    private static final String INCOME_LOCK_KEY  = "staged-income-validator";

    @Autowired StagedExpenseValidator expenseValidator;
    @Autowired StagedIncomeValidator incomeValidator;
    @Autowired DistributedLockService lockService;
    @Autowired ExpenseProducer expenseProducer;
    @Autowired IncomeProducer incomeProducer;
    @Autowired StagedExpenseRepository stagedExpenseRepo;
    @Autowired StagedIncomeRepository stagedIncomeRepo;
    @Autowired ExpenseRepository expenseRepo;
    @Autowired IncomeRepository incomeRepo;
    @Autowired InvestmentRepository investmentRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired AuthService authService;
    @Autowired InvestmentService investmentService;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        stagedExpenseRepo.deleteAll();
        stagedIncomeRepo.deleteAll();
        expenseRepo.deleteAll();
        incomeRepo.deleteAll();
        investmentRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("lock@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Lock Farm " + UUID.randomUUID());
        reg.setName("Lock Master");
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
        userId = authRepository.findByEmail("lock@test.local").orElseThrow().getId();

        InvestmentRequest inv = new InvestmentRequest();
        inv.setAmount(new BigDecimal("1000000.00"));
        inv.setCurrency(Currency.XAF);
        inv.setInvestorName("Lock Investor");
        inv.setInvestmentDate(LocalDate.now());
        inv.setActivate(true);
        investmentService.createInvestment(inv, farmId, userId);
    }

    @Test
    void expenseValidator_skipsWhileLockHeld_byPeer() {
        UUID submissionId = stageExpense();
        StagedExpense before = stagedExpenseRepo.findByIdOnly(submissionId).orElseThrow();
        assertThat(before.getStatus()).isEqualTo(StagedStatus.PENDING);

        // Peer holds the lock for the duration of our inner call.
        lockService.runIfLocked(EXPENSE_LOCK_KEY, Duration.ofSeconds(30), () -> {
            expenseValidator.processStagedExpenses();
            return null;
        });

        StagedExpense stillPending =
                stagedExpenseRepo.findByIdOnly(submissionId).orElseThrow();
        assertThat(stillPending.getStatus())
                .as("validator must skip because the lock is held by a peer")
                .isEqualTo(StagedStatus.PENDING);

        // Lock is released now; a second call must promote the row.
        expenseValidator.processStagedExpenses();

        StagedExpense after = stagedExpenseRepo.findByIdOnly(submissionId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(StagedStatus.MOVED);
        assertThat(after.getMovedExpenseId()).isNotNull();
    }

    @Test
    void incomeValidator_skipsWhileLockHeld_byPeer() {
        UUID submissionId = stageIncome();
        StagedIncome before = stagedIncomeRepo.findByIdOnly(submissionId).orElseThrow();
        assertThat(before.getStatus()).isEqualTo(StagedStatus.PENDING);

        lockService.runIfLocked(INCOME_LOCK_KEY, Duration.ofSeconds(30), () -> {
            incomeValidator.processStagedIncomes();
            return null;
        });

        StagedIncome stillPending =
                stagedIncomeRepo.findByIdOnly(submissionId).orElseThrow();
        assertThat(stillPending.getStatus()).isEqualTo(StagedStatus.PENDING);

        incomeValidator.processStagedIncomes();

        StagedIncome after = stagedIncomeRepo.findByIdOnly(submissionId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(StagedStatus.MOVED);
        assertThat(after.getMovedIncomeId()).isNotNull();
    }

    private UUID stageExpense() {
        UUID submissionId = UUID.randomUUID();
        expenseProducer.publish(new ExpenseSubmissionEvent(
                submissionId, farmId, userId, "lock@test.local",
                "{\"reason\":\"lock-test\",\"expenseDate\":\"" + LocalDate.now().minusDays(1)
                        + "\",\"qtyPurchased\":1,\"amountPaid\":50,\"expectedAmount\":50,"
                        + "\"currency\":\"XAF\",\"category\":\"MISCELLANEOUS\"}",
                LocalDateTime.now(), null, null));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stagedExpenseRepo.findByIdOnly(submissionId)).isPresent());
        return submissionId;
    }

    private UUID stageIncome() {
        UUID submissionId = UUID.randomUUID();
        incomeProducer.publish(new IncomeSubmissionEvent(
                submissionId, farmId, userId, "lock@test.local",
                "{\"reason\":\"lock-test\",\"incomeDate\":\"" + LocalDate.now().minusDays(1)
                        + "\",\"quantity\":1,\"amountReceived\":50,\"expectedAmount\":50,"
                        + "\"currency\":\"XAF\",\"category\":\"MISCELLANEOUS\"}",
                LocalDateTime.now(), null, null));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stagedIncomeRepo.findByIdOnly(submissionId)).isPresent());
        return submissionId;
    }
}