package com.agriva.personnel;

import com.agriva.TestcontainersConfiguration;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.finance.model.entity.Expense;
import com.agriva.finance.repository.ExpenseRepository;
import com.agriva.finance.repository.IncomeRepository;
import com.agriva.finance.repository.InvestmentRepository;
import com.agriva.finance.repository.TransactionRepository;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.payroll.repository.SalaryPaymentRepository;
import com.agriva.personnel.repository.PersonnelRepository;
import com.agriva.personnel.repository.PersonnelStatusHistoryRepository;
import com.agriva.shared.exception.InvestmentLimitExceededException;
import com.agriva.shared.exception.NoActiveInvestmentException;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.request.SubUserRegisterRequest;
import com.agriva.finance.model.dto.request.InvestmentRequest;
import com.agriva.finance.model.dto.response.InvestmentResponse;
import com.agriva.payroll.model.dto.requests.SalaryPaymentRequest;
import com.agriva.payroll.model.dto.response.SalaryPaymentResponse;
import com.agriva.payroll.model.dto.response.SalaryReceiptResponse;
import com.agriva.shared.models.enums.Currency;
import com.agriva.finance.model.enums.ExpenseCategory;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.payroll.model.enums.SalaryPaymentStatus;
import com.agriva.identity.service.AuthService;
import com.agriva.finance.service.InvestmentService;
import com.agriva.payroll.service.SalaryService;
import com.agriva.task.repository.TaskRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SalaryPaymentIT {

    @Autowired SalaryService salaryService;
    @Autowired InvestmentService investmentService;
    @Autowired AuthService authService;
    @Autowired
    PersonnelRepository personnelRepository;
    @Autowired
    PersonnelStatusHistoryRepository historyRepository;
    @Autowired
    TaskRepository taskRepository;
    @Autowired
    SalaryPaymentRepository salaryRepository;
    @Autowired
    ExpenseRepository expenseRepository;
    @Autowired
    InvestmentRepository investmentRepository;
    @Autowired
    IncomeRepository incomeRepository;
    @Autowired
    TransactionRepository transactionRepository;
    @Autowired
    AuthRepository authRepository;
    @Autowired
    FarmRepository farmRepository;

    UUID farmId;
    UUID masterUserId;
    UUID personnelId;

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
        reg.setEmail("paymaster@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Payroll Farm " + UUID.randomUUID());
        reg.setName("Payroll Master");
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
        masterUserId = authRepository.findByEmail("paymaster@test.local").orElseThrow().getId();

        SubUserRegisterRequest sub = new SubUserRegisterRequest();
        sub.setEmail("payee@test.local");
        sub.setName("Payee Person");
        sub.setUsername("payee");
        sub.setPassword("PayeePass12!");
        sub.setGender(Gender.FEMALE);
        sub.setDateOfBirth(LocalDate.now().minusYears(25));
        sub.setPhoneNumber("+237600000002");
        sub.setAddress("Rue 3");
        authService.registerSubUser(sub, farmId, "MASTER");

        UUID workerUserId = authRepository.findByEmail("payee@test.local")
                .orElseThrow().getId();
        personnelId = personnelRepository.findByUserId(workerUserId).orElseThrow().getId();
    }

    @Test
    void salary_createsLinkedExpenseAgainstActiveInvestment() {
        InvestmentResponse active = activateInvestment(new BigDecimal("100000"), Currency.XAF);

        SalaryPaymentResponse salary = paySalary(new BigDecimal("50000"));
        assertThat(salary.getExpenseId()).isNotNull();
        assertThat(salary.getStatus()).isEqualTo(SalaryPaymentStatus.PROCESSED);
        assertThat(salary.getPaymentReference()).startsWith("SAL-");

        Expense expense = expenseRepository.findById(salary.getExpenseId()).orElseThrow();
        assertThat(expense.getCategory()).isEqualTo(ExpenseCategory.LABOR_WAGES);
        assertThat(expense.getAmountPaid()).isEqualByComparingTo("50000");
        assertThat(expense.getInvestmentId()).isEqualTo(active.getId());
        assertThat(expense.getFarmId()).isEqualTo(farmId);
        assertThat(expense.getCurrency()).isEqualTo(Currency.XAF);
        assertThat(expense.getReason()).contains("Payee Person");
    }

    @Test
    void salaryWithoutActiveInvestment_isRejected() {
        SalaryPaymentRequest req = new SalaryPaymentRequest();
        req.setPersonnelId(personnelId);
        req.setAmount(new BigDecimal("50000"));
        req.setPaymentDate(LocalDate.now());

        assertThatThrownBy(() -> salaryService.create(req, farmId, masterUserId))
                .isInstanceOf(NoActiveInvestmentException.class)
                .hasMessageContaining("active investment");
    }

    @Test
    void salaryExceedingRemainingBudget_isRejected() {
        activateInvestment(new BigDecimal("30000"), Currency.XAF);

        SalaryPaymentRequest req = new SalaryPaymentRequest();
        req.setPersonnelId(personnelId);
        req.setAmount(new BigDecimal("50000"));
        req.setPaymentDate(LocalDate.now());

        assertThatThrownBy(() -> salaryService.create(req, farmId, masterUserId))
                .isInstanceOf(InvestmentLimitExceededException.class);

        assertThat(salaryRepository.count()).isZero();
        assertThat(expenseRepository.count()).isZero();
    }

    @Test
    void twoSalariesShareBudget() {
        activateInvestment(new BigDecimal("100000"), Currency.XAF);

        paySalary(new BigDecimal("40000"));
        paySalary(new BigDecimal("40000"));

        assertThat(salaryRepository.count()).isEqualTo(2);
        assertThat(expenseRepository.count()).isEqualTo(2);

        SalaryPaymentRequest third = new SalaryPaymentRequest();
        third.setPersonnelId(personnelId);
        third.setAmount(new BigDecimal("25000"));
        third.setPaymentDate(LocalDate.now());

        assertThatThrownBy(() -> salaryService.create(third, farmId, masterUserId))
                .isInstanceOf(InvestmentLimitExceededException.class);
    }

    @Test
    void receiptJson_containsPayeeAndPaymentDetails() {
        activateInvestment(new BigDecimal("100000"), Currency.XAF);
        SalaryPaymentResponse salary = paySalary(new BigDecimal("50000"));

        SalaryReceiptResponse receipt = salaryService.getReceipt(salary.getId(), farmId);
        assertThat(receipt.getPersonnelName()).isEqualTo("Payee Person");
        assertThat(receipt.getAmount()).isEqualByComparingTo("50000");
        assertThat(receipt.getReceiptNumber()).startsWith("RCPT-");
        assertThat(receipt.getPaymentReference()).isEqualTo(salary.getPaymentReference());
    }

    @Test
    void receiptPdf_isValidPdf() {
        activateInvestment(new BigDecimal("100000"), Currency.XAF);
        SalaryPaymentResponse salary = paySalary(new BigDecimal("50000"));

        byte[] pdf = salaryService.getReceiptPdf(salary.getId(), farmId);
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    }

    private InvestmentResponse activateInvestment(BigDecimal amount, Currency currency) {
        InvestmentRequest req = new InvestmentRequest();
        req.setAmount(amount);
        req.setCurrency(currency);
        req.setInvestorName("Test Fund");
        req.setInvestmentDate(LocalDate.now());
        req.setActivate(true);
        return investmentService.createInvestment(req, farmId, masterUserId);
    }

    private SalaryPaymentResponse paySalary(BigDecimal amount) {
        SalaryPaymentRequest req = new SalaryPaymentRequest();
        req.setPersonnelId(personnelId);
        req.setAmount(amount);
        req.setPaymentDate(LocalDate.now());
        return salaryService.create(req, farmId, masterUserId);
    }
}