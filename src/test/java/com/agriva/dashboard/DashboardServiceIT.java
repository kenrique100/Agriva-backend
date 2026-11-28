package com.agriva.dashboard;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.finance.model.dto.request.ExpenseRequest;
import com.agriva.finance.model.dto.request.IncomeRequest;
import com.agriva.finance.model.dto.request.TransactionRequest;
import com.agriva.finance.model.dto.response.TransactionResponse;
import com.agriva.finance.model.enums.ExpenseCategory;
import com.agriva.finance.model.enums.IncomeCategory;
import com.agriva.finance.repository.ExpenseRepository;
import com.agriva.finance.repository.IncomeRepository;
import com.agriva.finance.repository.InvestmentRepository;
import com.agriva.finance.repository.TransactionRepository;
import com.agriva.finance.service.ExpenseService;
import com.agriva.finance.service.IncomeService;
import com.agriva.finance.service.TransactionService;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.request.SubUserRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.dashboard.model.DashboardResponse;
import com.agriva.finance.model.dto.request.InvestmentRequest;
import com.agriva.shared.models.enums.Currency;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.identity.service.AuthService;
import com.agriva.dashboard.service.DashboardService;
import com.agriva.finance.service.InvestmentService;
import com.agriva.shared.security.RoleConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DashboardServiceIT {

    @Autowired DashboardService dashboardService;
    @Autowired AuthService authService;
    @Autowired InvestmentService investmentService;
    @Autowired IncomeService incomeService;
    @Autowired ExpenseService expenseService;
    @Autowired TransactionService transactionService;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired InvestmentRepository investmentRepo;
    @Autowired IncomeRepository incomeRepo;
    @Autowired ExpenseRepository expenseRepo;
    @Autowired TransactionRepository transactionRepo;

    UUID farmId;
    UUID masterId;
    UUID subUserId;

    @BeforeEach
    void setUp() {
        // Children first to respect FK order.
        expenseRepo.deleteAll();
        incomeRepo.deleteAll();
        transactionRepo.deleteAll();
        investmentRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("dash-master@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Dashboard Test " + UUID.randomUUID());
        reg.setName("Dash Master");
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
        masterId = authRepository.findByEmail("dash-master@test.local").orElseThrow().getId();

        SubUserRegisterRequest sub = new SubUserRegisterRequest();
        sub.setEmail("dash-sub@test.local");
        sub.setPassword("SubPass1234!");
        sub.setName("Dash Sub");
        sub.setGender(Gender.FEMALE);
        sub.setDateOfBirth(LocalDate.now().minusYears(25));
        sub.setPhoneNumber("+237600000001");
        sub.setAddress("Rue 2");
        authService.registerSubUser(sub, farmId, "MASTER");
        subUserId = authRepository.findByEmail("dash-sub@test.local").orElseThrow().getId();

        // Must be ACTIVE so ExpenseService.createDirect() can link expenses to it.
        InvestmentRequest inv = new InvestmentRequest();
        inv.setAmount(new BigDecimal("5000"));
        inv.setInvestorName("Fund A");
        inv.setInvestmentDate(LocalDate.now().minusDays(5));
        inv.setActivate(true);
        investmentService.createInvestment(inv, farmId, masterId);
    }

    @Test
    void master_dashboard_includesInvestmentFields() {
        DashboardResponse resp = dashboardService.personalDashboard(masterId, farmId, "MASTER");

        assertThat(resp.getFarm()).isNotNull();
        assertThat(resp.getFarm().getTotalInvestments()).isNotNull();
        assertThat(resp.getFarm().getTotalInvestment()).isEqualByComparingTo("5000");
        assertThat(resp.getFarm().getNetPosition()).isNotNull();
    }

    @Test
    void subUser_dashboard_hidesInvestmentFields() {
        DashboardResponse resp = dashboardService.personalDashboard(subUserId, farmId, "SUB_USER");

        assertThat(resp.getFarm()).isNotNull();
        assertThat(resp.getFarm().getTotalInvestments()).isNull();
        assertThat(resp.getFarm().getTotalInvestment()).isNull();
        assertThat(resp.getFarm().getNetPosition()).isNull();
    }

    @Test
    void farmDashboard_returnsUsersAndTransactions() {
        TransactionRequest txReq = new TransactionRequest();
        txReq.setNotes("Dashboard test transaction");
        TransactionResponse tx = transactionService.create(txReq, farmId, subUserId);

        IncomeRequest incomeReq = new IncomeRequest();
        incomeReq.setAmountReceived(new BigDecimal("1000"));
        incomeReq.setCurrency(Currency.XAF);
        incomeReq.setReason("Sale");
        incomeReq.setCategory(IncomeCategory.CROP_SALES);
        incomeReq.setIncomeDate(LocalDate.now());
        incomeReq.setTransactionId(tx.getId());
        incomeService.createDirect(incomeReq, farmId, subUserId);

        ExpenseRequest expenseReq = new ExpenseRequest();
        expenseReq.setAmountPaid(new BigDecimal("400"));
        expenseReq.setCurrency(Currency.XAF);
        expenseReq.setReason("Feed");
        expenseReq.setCategory(ExpenseCategory.ANIMAL_FEED);
        expenseReq.setExpenseDate(LocalDate.now());
        expenseReq.setTransactionId(tx.getId());
        expenseService.createDirect(expenseReq, farmId, subUserId);

        DashboardResponse dashboard = dashboardService.farmDashboard(farmId, masterId);
        assertThat(dashboard.getUsers())
                .as("D3: owner (MASTER) excluded from user-activity list")
                .hasSize(1);

        DashboardResponse.UserActivity only = dashboard.getUsers().get(0);
        assertThat(only.getEmail()).isEqualTo("dash-sub@test.local");
        assertThat(only.getRole()).isEqualTo(RoleConstants.SUB_USER);
        assertThat(only.getActivitySummary().getTotalIncomes()).isEqualTo(1L);
        assertThat(only.getActivitySummary().getTotalExpenses()).isEqualTo(1L);
        assertThat(only.getActivitySummary().getTotalIncome())
                .isEqualByComparingTo("1000");
        assertThat(only.getActivitySummary().getTotalExpense())
                .isEqualByComparingTo("400");

        assertThat(dashboard.getUsers())
                .extracting(DashboardResponse.UserActivity::getEmail)
                .doesNotContain("dash-master@test.local");

        assertThat(dashboard.getFarm().getTotalIncomes()).isEqualTo(1L);
        assertThat(dashboard.getFarm().getTotalExpenses()).isEqualTo(1L);

        assertThat(dashboard.getTransactions()).isNotEmpty();
        DashboardResponse.TransactionSummary summary = dashboard.getTransactions().get(0);
        assertThat(summary.getId()).isEqualTo(tx.getId());
        assertThat(summary.getIncomeLines()).hasSize(1);
        assertThat(summary.getExpenseLines()).hasSize(1);
        assertThat(summary.getIncome()).isEqualByComparingTo("1000");
        assertThat(summary.getExpense()).isEqualByComparingTo("400");
    }

    @Test
    void master_dashboard_setsTotalsToNullWhenCategoryHasMixedCurrencies() {
        InvestmentRequest inv = new InvestmentRequest();
        inv.setAmount(new BigDecimal("1200"));
        inv.setCurrency(Currency.EUR);
        inv.setInvestorName("Fund B");
        inv.setInvestmentDate(LocalDate.now().minusDays(2));
        investmentService.createInvestment(inv, farmId, masterId);

        DashboardResponse resp = dashboardService.personalDashboard(masterId, farmId, "MASTER");

        assertThat(resp.getFarm()).isNotNull();
        assertThat(resp.getFarm().getTotalInvestment()).isNull();
        assertThat(resp.getFarm().getNetPosition()).isNull();
    }
}