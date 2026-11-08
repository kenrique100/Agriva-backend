package com.agriva.dashboard;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.finance.repository.InvestmentRepository;
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
    @Autowired
    AuthRepository authRepository;
    @Autowired
    FarmRepository farmRepository;
    @Autowired
    InvestmentRepository investmentRepo;

    UUID farmId;
    UUID masterId;
    UUID subUserId;

    @BeforeEach
    void setUp() {
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

        InvestmentRequest inv = new InvestmentRequest();
        inv.setAmount(new BigDecimal("5000"));
        inv.setInvestorName("Fund A");
        inv.setInvestmentDate(LocalDate.now().minusDays(5));
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
        DashboardResponse resp = dashboardService.farmDashboard(farmId, masterId);

        assertThat(resp.getFarm()).isNotNull();
        assertThat(resp.getUsers()).hasSize(2);
        assertThat(resp.getTransactions()).isNotNull();
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