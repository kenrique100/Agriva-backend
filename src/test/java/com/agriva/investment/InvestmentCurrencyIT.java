package com.agriva.investment;

import com.agriva.TestcontainersConfiguration;
import com.agriva.model.dto.auth.AuthResponse;
import com.agriva.model.dto.auth.MasterRegisterRequest;
import com.agriva.model.dto.investment.InvestmentRequest;
import com.agriva.model.dto.investment.InvestmentResponse;
import com.agriva.model.entity.enums.Currency;
import com.agriva.model.entity.enums.Gender;
import com.agriva.model.entity.Investment;
import com.agriva.repository.*;
import com.agriva.service.AuthService;
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
class InvestmentCurrencyIT {

    @Autowired InvestmentService investmentService;
    @Autowired AuthService authService;
    @Autowired InvestmentRepository investmentRepository;
    @Autowired PersonnelRepository personnelRepository;
    @Autowired PersonnelStatusHistoryRepository historyRepository;
    @Autowired TaskRepository taskRepository;
    @Autowired SalaryPaymentRepository salaryRepository;
    @Autowired ExpenseRepository expenseRepository;
    @Autowired IncomeRepository incomeRepository;
    @Autowired TransactionRepository transactionRepository;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;
    UUID masterUserId;

    @BeforeEach
    void setUp() {
        salaryRepository.deleteAll();
        taskRepository.deleteAll();
        historyRepository.deleteAll();
        personnelRepository.deleteAll();
        expenseRepository.deleteAll();
        investmentRepository.deleteAll();
        incomeRepository.deleteAll();
        transactionRepository.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("curr@test.local");
        reg.setPassword("MasterPass1");
        reg.setFarmName("Currency Farm " + UUID.randomUUID());
        reg.setName("Currency Master");
        reg.setGender(Gender.MALE);
        reg.setDateOfBirth(LocalDate.now().minusYears(35));
        reg.setPhoneNumber("+237600000000");
        reg.setCountry("Cameroon"); reg.setState("Centre"); reg.setCity("Yaoundé");
        reg.setAddress("Rue 1"); reg.setPostalCode("00000");
        reg.setCurrency("XAF"); reg.setUserAgreement(true);
        AuthResponse master = authService.registerMaster(reg);
        farmId = master.getFarmId();
        masterUserId = authRepository.findByEmail("curr@test.local").orElseThrow().getId();
    }

    @Test
    void investmentCurrency_isEnforcedAsEnumRoundTrip() {
        InvestmentRequest req = new InvestmentRequest();
        req.setAmount(new BigDecimal("1000"));
        req.setCurrency(Currency.EUR);
        req.setInvestorName("Euro Fund");
        req.setInvestmentDate(LocalDate.now());

        InvestmentResponse created = investmentService.createInvestment(req, farmId, masterUserId);
        assertThat(created.getCurrency()).isEqualTo(Currency.EUR);

        // DB round-trip
        Investment raw = investmentRepository.findById(created.getId()).orElseThrow();
        assertThat(raw.getCurrency()).isEqualTo(Currency.EUR);

        // Service re-read
        InvestmentResponse reread = investmentService.getInvestment(created.getId(), farmId);
        assertThat(reread.getCurrency()).isEqualTo(Currency.EUR);
    }

    @Test
    void investmentCurrency_nullDefaultsToUsd() {
        InvestmentRequest req = new InvestmentRequest();
        req.setAmount(new BigDecimal("1000"));
        req.setCurrency(null); // omitted
        req.setInvestorName("Anonymous");
        req.setInvestmentDate(LocalDate.now());

        InvestmentResponse created = investmentService.createInvestment(req, farmId, masterUserId);
        assertThat(created.getCurrency()).isEqualTo(Currency.USD);
    }

    @Test
    void investmentCurrency_survivesUpdate() {
        InvestmentRequest req = new InvestmentRequest();
        req.setAmount(new BigDecimal("1000"));
        req.setCurrency(Currency.GBP);
        req.setInvestorName("UK Fund");
        req.setInvestmentDate(LocalDate.now());
        InvestmentResponse created = investmentService.createInvestment(req, farmId, masterUserId);

        req.setAmount(new BigDecimal("2000"));
        req.setCurrency(Currency.CHF);
        InvestmentResponse updated = investmentService.updateInvestment(created.getId(), req, farmId);

        assertThat(updated.getCurrency()).isEqualTo(Currency.CHF);
    }
}