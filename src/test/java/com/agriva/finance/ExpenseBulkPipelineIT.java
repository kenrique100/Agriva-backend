package com.agriva.finance;

import com.agriva.TestcontainersConfiguration;
import com.agriva.finance.model.dto.request.InvestmentRequest;
import com.agriva.finance.model.dto.response.BatchStatusResponse;
import com.agriva.finance.model.dto.response.BatchUploadResponse;
import com.agriva.finance.model.entity.StagedExpense;
import com.agriva.finance.repository.ExpenseRepository;
import com.agriva.finance.repository.InvestmentRepository;
import com.agriva.finance.repository.StagedExpenseRepository;
import com.agriva.finance.scheduler.StagedExpenseValidator;
import com.agriva.finance.service.ExpenseService;
import com.agriva.finance.service.InvestmentService;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.shared.models.enums.Currency;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
        // Deterministic: the test calls processStagedExpenses() itself, so we
        // shut off the cron schedule instead of racing it.
        "agriva.expense.validator.cron=0 0 0 1 1 *",
        "agriva.expense.validator.retry-delay-minutes=0",
        "agriva.expense.validator.max-attempts=4"
})
class ExpenseBulkPipelineIT {

    @Autowired ExpenseService expenseService;
    @Autowired InvestmentService investmentService;
    @Autowired StagedExpenseValidator validator;
    @Autowired ExpenseRepository expenseRepo;
    @Autowired StagedExpenseRepository stagedRepo;
    @Autowired InvestmentRepository investmentRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired AuthService authService;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        stagedRepo.deleteAll();
        expenseRepo.deleteAll();
        investmentRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("bulk-expense@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("BulkExpense Farm " + UUID.randomUUID());
        reg.setName("BulkExpense Master");
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
        userId = authRepository.findByEmail("bulk-expense@test.local").orElseThrow().getId();

        InvestmentRequest inv = new InvestmentRequest();
        inv.setAmount(new BigDecimal("1000000.00"));
        inv.setCurrency(Currency.XAF);
        inv.setInvestorName("Bulk Investor");
        inv.setInvestmentDate(LocalDate.now());
        inv.setActivate(true);
        investmentService.createInvestment(inv, farmId, userId);
    }

    @Test
    void allValidRows_areQueued_thenPromotedToExpenseTable() throws Exception {
        byte[] xlsx = excel(
                row("Valid A", LocalDate.now().minusDays(3), "100", "100"),
                row("Valid B", LocalDate.now().minusDays(2), "200", "200"),
                row("Valid C", LocalDate.now().minusDays(1), "300", "300")
        );

        BatchUploadResponse resp = expenseService.bulkUpload(
                multipart(xlsx), farmId, userId, "bulk-expense@test.local");

        assertThat(resp.getBatchId()).isNotNull();
        assertThat(resp.getTotalRows()).isEqualTo(3);
        assertThat(resp.getQueued()).isEqualTo(3);
        assertThat(resp.getFailed()).isZero();

        // Wait for all rows to reach staging (Kafka → consumer → staged_*).
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stagedRepo.findByFarmIdAndBatchIdOrderByRowNumberAsc(
                        farmId, resp.getBatchId())).hasSize(3));

        // Drive the validator once — all rows should pass and move.
        validator.processStagedExpenses();

        BatchStatusResponse status = expenseService.bulkStatus(resp.getBatchId(), farmId);
        assertThat(status.getTotal()).isEqualTo(3);
        assertThat(status.getPending()).isZero();
        assertThat(status.getMoved()).isEqualTo(3);
        assertThat(status.getFailed()).isZero();
        assertThat(status.getFailedRows()).isNull();

        assertThat(expenseRepo.countByFarmId(farmId)).isEqualTo(3);
    }

    @Test
    void mixedBatch_validRowsMove_invalidRowsReportedWithRowNumbersAndErrors() throws Exception {
        // 3 valid + 2 with negative amount (validation failure; needs 4 retries
        // to exhaust the budget — driven explicitly below).
        byte[] xlsx = excel(
                row("Good 1", LocalDate.now().minusDays(5), "150", "150"),
                row("Good 2", LocalDate.now().minusDays(4), "250", "250"),
                row("Good 3", LocalDate.now().minusDays(3), "350", "350"),
                row("Bad 1",  LocalDate.now().minusDays(2), "-100", "100"),
                row("Bad 2",  LocalDate.now().minusDays(1), "-200", "200")
        );

        BatchUploadResponse resp = expenseService.bulkUpload(
                multipart(xlsx), farmId, userId, "bulk-expense@test.local");
        UUID batchId = resp.getBatchId();

        assertThat(resp.getTotalRows()).isEqualTo(5);
        // Parse-time: all 5 are syntactically valid — semantic checks happen
        // async, so queued == 5 here.
        assertThat(resp.getQueued()).isEqualTo(5);
        assertThat(resp.getFailed()).isZero();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stagedRepo.findByFarmIdAndBatchIdOrderByRowNumberAsc(farmId, batchId))
                        .hasSize(5));

        // max-attempts=4 → drive it four times to exhaust the invalid rows.
        for (int i = 0; i < 4; i++) {
            validator.processStagedExpenses();
        }

        BatchStatusResponse status = expenseService.bulkStatus(batchId, farmId);
        assertThat(status.getTotal()).isEqualTo(5);
        assertThat(status.getPending()).isZero();
        assertThat(status.getMoved()).isEqualTo(3);
        assertThat(status.getFailed()).isEqualTo(2);

        assertThat(status.getFailedRows()).hasSize(2);
        assertThat(status.getFailedRows())
                .extracting(BatchStatusResponse.FailedRow::getRowNumber)
                .containsExactlyInAnyOrder(4, 5);                 // the two "Bad" rows
        assertThat(status.getFailedRows())
                .allSatisfy(f -> assertThat(f.getErrors())
                        .contains("Amount paid must be positive"));
        assertThat(status.getFailedRows())
                .allSatisfy(f -> assertThat(f.getSubmissionId()).isNotNull());

        // Main table received only the 3 valid rows.
        assertThat(expenseRepo.countByFarmId(farmId)).isEqualTo(3);
    }

    private MockMultipartFile multipart(byte[] bytes) {
        return new MockMultipartFile(
                "file", "expenses.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);
    }

    private byte[] excel(String[]... rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Expenses");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("reason");
            header.createCell(1).setCellValue("expenseDate");
            header.createCell(2).setCellValue("amountPaid");
            header.createCell(3).setCellValue("expectedAmount");
            header.createCell(4).setCellValue("currency");
            header.createCell(5).setCellValue("category");

            for (int i = 0; i < rows.length; i++) {
                Row r = sheet.createRow(i + 1);
                for (int j = 0; j < rows[i].length; j++) {
                    r.createCell(j).setCellValue(rows[i][j]);
                }
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    private String[] row(String reason, LocalDate date, String amountPaid, String expected) {
        return new String[]{
                reason, date.toString(), amountPaid, expected, "XAF", "MISCELLANEOUS"
        };
    }
}