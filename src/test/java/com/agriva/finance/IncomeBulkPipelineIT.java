package com.agriva.finance;

import com.agriva.TestcontainersConfiguration;
import com.agriva.finance.repository.IncomeRepository;
import com.agriva.finance.repository.InvestmentRepository;
import com.agriva.finance.repository.StagedIncomeRepository;
import com.agriva.finance.scheduler.StagedIncomeValidator;
import com.agriva.finance.service.IncomeService;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.identity.models.dto.request.MasterRegisterRequest;
import com.agriva.identity.models.dto.response.AuthResponse;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.identity.service.AuthService;
import com.agriva.personnel.models.enums.Gender;
import com.agriva.shared.models.dto.BatchStatusResponse;
import com.agriva.shared.models.dto.BatchUploadResponse;
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
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "agriva.income.validator.cron=0 0 0 1 1 *",
        "agriva.income.validator.retry-delay-minutes=0",
        "agriva.income.validator.max-attempts=4"
})
class IncomeBulkPipelineIT {

    @Autowired IncomeService incomeService;
    @Autowired StagedIncomeValidator validator;
    @Autowired IncomeRepository incomeRepo;
    @Autowired StagedIncomeRepository stagedRepo;
    @Autowired InvestmentRepository investmentRepo;
    @Autowired AuthRepository authRepository;
    @Autowired FarmRepository farmRepository;
    @Autowired AuthService authService;

    UUID farmId;
    UUID userId;

    @BeforeEach
    void setUp() {
        stagedRepo.deleteAll();
        incomeRepo.deleteAll();
        investmentRepo.deleteAll();
        authRepository.deleteAll();
        farmRepository.deleteAll();

        MasterRegisterRequest reg = new MasterRegisterRequest();
        reg.setEmail("bulk-income@test.local");
        reg.setPassword("MasterPass1!");
        reg.setFarmName("BulkIncome Farm " + UUID.randomUUID());
        reg.setName("BulkIncome Master");
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
        userId = authRepository.findByEmail("bulk-income@test.local").orElseThrow().getId();
    }

    @Test
    void allValidRows_areQueued_thenPromotedToIncomeTable() throws Exception {
        byte[] xlsx = excel(
                row("Sale A", LocalDate.now().minusDays(3), "1000", "1000"),
                row("Sale B", LocalDate.now().minusDays(2), "2000", "2000"),
                row("Sale C", LocalDate.now().minusDays(1), "3000", "3000")
        );

        BatchUploadResponse resp = incomeService.bulkUpload(
                multipart(xlsx), farmId, userId, "bulk-income@test.local");

        assertThat(resp.getBatchId()).isNotNull();
        assertThat(resp.getQueued()).isEqualTo(3);
        assertThat(resp.getFailed()).isZero();

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stagedRepo.findByFarmIdAndBatchIdOrderByRowNumberAsc(
                        farmId, resp.getBatchId())).hasSize(3));

        validator.processStagedIncomes();

        BatchStatusResponse status = incomeService.bulkStatus(resp.getBatchId(), farmId);
        assertThat(status.getPending()).isZero();
        assertThat(status.getMoved()).isEqualTo(3);
        assertThat(status.getFailed()).isZero();
        assertThat(incomeRepo.countByFarmId(farmId)).isEqualTo(3);
    }

    @Test
    void mixedBatch_validRowsMove_invalidRowsReportedWithRowNumbersAndErrors() throws Exception {
        byte[] xlsx = excel(
                row("Good 1", LocalDate.now().minusDays(5), "100", "100"),
                row("Good 2", LocalDate.now().minusDays(4), "200", "200"),
                row("Good 3", LocalDate.now().minusDays(3), "300", "300"),
                row("Bad 1",  LocalDate.now().minusDays(2), "-1", "100"),
                row("Bad 2",  LocalDate.now().minusDays(1), "-2", "100")
        );

        BatchUploadResponse resp = incomeService.bulkUpload(
                multipart(xlsx), farmId, userId, "bulk-income@test.local");
        UUID batchId = resp.getBatchId();

        assertThat(resp.getQueued()).isEqualTo(5);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(stagedRepo.findByFarmIdAndBatchIdOrderByRowNumberAsc(farmId, batchId))
                        .hasSize(5));

        for (int i = 0; i < 4; i++) {
            validator.processStagedIncomes();
        }

        BatchStatusResponse status = incomeService.bulkStatus(batchId, farmId);
        assertThat(status.getPending()).isZero();
        assertThat(status.getMoved()).isEqualTo(3);
        assertThat(status.getFailed()).isEqualTo(2);

        assertThat(status.getFailedRows()).hasSize(2);
        assertThat(status.getFailedRows())
                .extracting(BatchStatusResponse.FailedRow::getRowNumber)
                .containsExactlyInAnyOrder(4, 5);
        assertThat(status.getFailedRows())
                .allSatisfy(f -> assertThat(f.getErrors())
                        .contains("Amount received must be positive"));

        assertThat(incomeRepo.countByFarmId(farmId)).isEqualTo(3);
    }

    private MockMultipartFile multipart(byte[] bytes) {
        return new MockMultipartFile(
                "file", "incomes.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes);
    }

    private byte[] excel(String[]... rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Incomes");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("reason");
            header.createCell(1).setCellValue("incomeDate");
            header.createCell(2).setCellValue("amountReceived");
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

    private String[] row(String reason, LocalDate date, String amountReceived, String expected) {
        return new String[]{
                reason, date.toString(), amountReceived, expected, "XAF", "MISCELLANEOUS"
        };
    }
}