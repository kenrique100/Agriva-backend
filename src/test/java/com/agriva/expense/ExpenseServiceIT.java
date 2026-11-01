package com.agriva.expense;

import com.agriva.TestcontainersConfiguration;
import com.agriva.model.dto.auth.AuthResponse;
import com.agriva.model.dto.auth.MasterRegisterRequest;
import com.agriva.model.dto.expense.*;
import com.agriva.model.entity.enums.Currency;
import com.agriva.model.entity.Expense;
import com.agriva.model.entity.enums.ExpenseCategory;
import com.agriva.model.entity.StagedExpense;
import com.agriva.model.entity.enums.StagedExpenseStatus;
import com.agriva.repository.AuthRepository;
import com.agriva.repository.ExpenseRepository;
import com.agriva.repository.FarmRepository;
import com.agriva.repository.StagedExpenseRepository;
import com.agriva.scheduler.StagedExpenseValidator;
import com.agriva.service.AuthService;
import com.agriva.service.ExpenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ExpenseServiceIT {

    @Autowired ExpenseService expenseService;
    @Autowired StagedExpenseValidator validator;
    @Autowired AuthService authService;
    @Autowired ExpenseRepository expenseRepo;
    @Autowired StagedExpenseRepository stagedRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;

    UUID farmId;

    @BeforeEach
    void setUp() {
        stagedRepo.deleteAll();
        expenseRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("expense@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("Expense Test Farm " + UUID.randomUUID());
        AuthResponse master = authService.registerMaster(reg);
        farmId = master.getFarmId();
    }

    @Test
    void submitExpense_movesToExpenseTable() {
        ExpenseRequest req = new ExpenseRequest();
        req.setAmountPaid(new BigDecimal("250.00"));
        req.setExpectedAmount(new BigDecimal("250.00"));
        req.setQtyPurchased(1);
        req.setCurrency(Currency.USD);
        req.setCategory(ExpenseCategory.ANIMAL_FEED);
        req.setReason("Agro Supply");
        req.setExpenseDate(LocalDate.now().minusDays(2));
        req.setReceipt("REC-001");

        ExpenseSubmissionAck ack = expenseService.submitExpense(
                req, farmId, null, "expense@test.local");

        assertThat(ack.getStatus()).isEqualTo("QUEUED");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            StagedExpense staged = stagedRepo.findById(ack.getSubmissionId()).orElse(null);
            assertThat(staged).isNotNull();
        });

        validator.processStagedExpenses();

        StagedExpense staged = stagedRepo.findById(ack.getSubmissionId()).orElseThrow();
        assertThat(staged.getStatus()).isEqualTo(StagedExpenseStatus.MOVED);

        Expense saved = expenseRepo.findAll().get(0);
        assertThat(saved.getAmountPaid()).isEqualByComparingTo("250.00");
        assertThat(saved.getReason()).isEqualTo("Agro Supply");
        assertThat(saved.getQtyPurchased()).isEqualTo(1);
    }

    @Test
    void createThenList_updateDelete() {
        UUID userId = authRepository.findByEmail("expense@test.local").orElseThrow().getId();

        ExpenseRequest req = new ExpenseRequest();
        req.setAmountPaid(new BigDecimal("100"));
        req.setExpectedAmount(new BigDecimal("100"));
        req.setQtyPurchased(1);
        req.setCurrency(Currency.USD);
        req.setCategory(ExpenseCategory.MISCELLANEOUS);
        req.setReason("Vendor A");
        req.setExpenseDate(LocalDate.now().minusDays(1));

        var created = expenseService.createDirect(req, farmId, userId);
        assertThat(created.getId()).isNotNull();

        var page = expenseService.listExpenses(farmId, org.springframework.data.domain.Pageable.unpaged());
        assertThat(page.getTotalElements()).isEqualTo(1);

        expenseService.deleteExpense(created.getId(), farmId);
        // deleteExpense soft-deletes → findAll() still sees the row
        assertThat(expenseRepo.findAll())
                .allMatch(e -> e.getDeletedAt() != null);
    }
}