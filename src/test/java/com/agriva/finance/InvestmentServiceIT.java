package com.agriva.finance;

import com.agriva.TestcontainersConfiguration;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.finance.model.dto.request.InvestmentRequest;
import com.agriva.finance.model.dto.response.InvestmentResponse;
import com.agriva.shared.models.enums.Currency;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.finance.repository.InvestmentRepository;
import com.agriva.identity.service.AuthService;
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
class InvestmentServiceIT {

    @Autowired InvestmentService investmentService;
    @Autowired AuthService authService;
    @Autowired InvestmentRepository investmentRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        investmentRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("invest@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Investment Test Farm " + UUID.randomUUID());
        AuthResponse master = authService.registerMaster(reg);
        farmId = master.getFarmId();
        userId = authRepository.findByEmail("invest@test.local").orElseThrow().getId();
    }

    @Test
    void createAndList() {
        InvestmentRequest req = new InvestmentRequest();
        req.setAmount(new BigDecimal("5000"));
        req.setCurrency(Currency.USD);
        req.setInvestorName("East Africa Agri Fund");
        req.setType("EQUIPMENT");
        req.setDescription("Industrial water pump");
        req.setInvestmentDate(LocalDate.now().minusDays(3));

        InvestmentResponse created = investmentService.createInvestment(req, farmId, userId);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getAmount()).isEqualByComparingTo("5000");
        assertThat(created.getCurrency()).isEqualTo(Currency.USD);

        var page = investmentService.listInvestments(
                farmId, org.springframework.data.domain.Pageable.unpaged());
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void updateAndDelete() {
        InvestmentRequest req = new InvestmentRequest();
        req.setAmount(new BigDecimal("1000"));
        req.setInvestorName("Self");
        req.setInvestmentDate(LocalDate.now().minusDays(1));
        InvestmentResponse created = investmentService.createInvestment(req, farmId, userId);

        req.setAmount(new BigDecimal("2000"));
        InvestmentResponse updated = investmentService.updateInvestment(created.getId(), req, farmId);
        assertThat(updated.getAmount()).isEqualByComparingTo("2000");

        investmentService.deleteInvestment(created.getId(), farmId);
        // InvestmentServiceImpl.deleteInvestment soft-deletes (setDeletedAt)
        assertThat(investmentRepo.findById(created.getId()))
                .hasValueSatisfying(i -> assertThat(i.getDeletedAt()).isNotNull());
    }
}