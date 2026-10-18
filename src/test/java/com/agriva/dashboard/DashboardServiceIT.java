package com.agriva.dashboard;

import com.agriva.TestcontainersConfiguration;
import com.agriva.model.dto.auth.*;
import com.agriva.model.dto.dashboard.DashboardResponse;
import com.agriva.model.dto.investment.InvestmentRequest;
import com.agriva.repository.*;
import com.agriva.service.AuthService;
import com.agriva.service.DashboardService;
import com.agriva.service.InvestmentService;
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
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired InvestmentRepository investmentRepo;

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
        reg.setPassword("MasterPass1");
        reg.setFarmName("Dashboard Test " + UUID.randomUUID());
        AuthResponse master = authService.registerMaster(reg);
        farmId = master.getFarmId();
        masterId = authRepository.findByEmail("dash-master@test.local").orElseThrow().getId();

        SubUserRegisterRequest sub = new SubUserRegisterRequest();
        sub.setEmail("dash-sub@test.local");
        sub.setPassword("SubPass123");
        sub.setFarmId(farmId);
        authService.registerSubUser(sub);
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
}