package com.agriva.payroll.service.impl;

import com.agriva.farming.model.entity.Farm;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.finance.model.entity.Expense;
import com.agriva.finance.model.entity.Investment;
import com.agriva.finance.model.enums.ExpenseCategory;
import com.agriva.finance.repository.ExpenseRepository;
import com.agriva.finance.repository.InvestmentRepository;
import com.agriva.finance.service.ExpenseService;
import com.agriva.finance.model.dto.request.ExpenseRequest;
import com.agriva.finance.model.dto.response.ExpenseResponse;
import com.agriva.payroll.kafka.producer.SalaryProducer;
import com.agriva.payroll.mapper.SalaryMapper;
import com.agriva.payroll.model.dto.requests.SalaryPaymentRequest;
import com.agriva.payroll.model.dto.requests.SalarySubmissionEvent;
import com.agriva.payroll.model.dto.response.*;
import com.agriva.payroll.model.entity.SalaryPayment;
import com.agriva.payroll.model.entity.StagedSalary;
import com.agriva.payroll.model.enums.SalaryPaymentStatus;
import com.agriva.payroll.model.enums.StagedSalaryStatus;
import com.agriva.payroll.repository.SalaryPaymentRepository;
import com.agriva.payroll.repository.StagedSalaryRepository;
import com.agriva.payroll.service.PdfReceiptService;
import com.agriva.payroll.service.SalaryService;
import com.agriva.payroll.utils.SalaryExcelParser;
import com.agriva.payroll.utils.SalaryValidator;
import com.agriva.personnel.models.entity.Personnel;
import com.agriva.personnel.repository.PersonnelRepository;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.NoActiveInvestmentException;
import com.agriva.shared.exception.PersonnelNotFoundException;
import com.agriva.shared.exception.SalaryPaymentNotFoundException;
import com.agriva.shared.models.enums.Currency;
import com.agriva.task.model.entity.Task;
import com.agriva.task.model.enums.TaskStatus;
import com.agriva.task.repository.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalaryServiceImpl implements SalaryService {

    private final SalaryPaymentRepository salaryRepository;
    private final PersonnelRepository personnelRepository;
    private final TaskRepository taskRepository;
    private final ExpenseRepository expenseRepository;
    private final InvestmentRepository investmentRepository;
    private final FarmRepository farmRepository;
    private final StagedSalaryRepository stagedRepo;
    private final ExpenseService expenseService;
    private final SalaryValidator validator;
    private final SalaryMapper mapper;
    private final PdfReceiptService pdfReceiptService;
    private final SalaryProducer salaryProducer;
    private final SalaryExcelParser excelParser;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public SalaryPaymentResponse create(SalaryPaymentRequest req, UUID farmId, UUID actorUserId) {
        Personnel p = personnelRepository.findByIdAndFarmIdLive(req.getPersonnelId(), farmId)
                .orElseThrow(() -> new PersonnelNotFoundException(
                        "Personnel not found with ID: " + req.getPersonnelId()));

        if (Boolean.TRUE.equals(p.getIsFarmOwner())) {
            throw new BusinessException("The farm owner cannot be paid a salary here.");
        }
        validator.validate(p.getStatus() != null ? p.getStatus().name() : null);

        Investment active = investmentRepository.findFirstByFarmIdAndActiveTrue(farmId)
                .orElseThrow(() -> new NoActiveInvestmentException(
                        "No active investment for this farm. Activate an investment before paying salaries."));
        Currency budgetCurrency = active.getCurrency();

        ExpenseRequest expenseRequest = new ExpenseRequest();
        expenseRequest.setReason("Salary — " + p.getName() + " — " + req.getPaymentDate());
        expenseRequest.setExpenseDate(req.getPaymentDate());
        expenseRequest.setQtyPurchased(1);
        expenseRequest.setAmountPaid(req.getAmount());
        expenseRequest.setExpectedAmount(req.getAmount());
        expenseRequest.setCategory(ExpenseCategory.LABOR_WAGES);
        expenseRequest.setCurrency(budgetCurrency);

        ExpenseResponse expense = expenseService.createDirect(expenseRequest, farmId, actorUserId);

        SalaryPayment sp = SalaryPayment.builder()
                .farmId(farmId)
                .personnelId(p.getId())
                .amount(req.getAmount())
                .paymentDate(req.getPaymentDate())
                .status(SalaryPaymentStatus.PROCESSED)
                .paymentReference(generateReference())
                .expenseId(expense.getId())
                .paidByUserId(actorUserId)
                .build();

        SalaryPayment saved;
        try {
            saved = salaryRepository.saveAndFlush(sp);
        } catch (DataIntegrityViolationException e) {
            if (isUniqueReferenceViolation(e)) {
                throw new BusinessException(
                        "Payment reference collision — please retry the request. "
                                + "This is exceedingly rare.");
            }
            throw e;
        }

        log.info("Salary {} paid for personnel {} — expense {}",
                saved.getId(), p.getId(), expense.getId());
        return mapper.toResponse(saved);
    }

    private static final int REF_SUFFIX_LEN = 12;

    private String generateReference() {
        String candidate;
        int attempt = 0;
        do {
            String suffix = UUID.randomUUID().toString().replace("-", "")
                    .substring(0, REF_SUFFIX_LEN).toUpperCase();
            candidate = "SAL-" + LocalDate.now().getYear() + "-" + suffix;
            attempt++;
        } while (salaryRepository.existsByPaymentReference(candidate) && attempt < 10);
        return candidate;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SalaryPaymentResponse> list(UUID farmId, UUID personnelId, Pageable pageable) {
        Page<SalaryPayment> page = personnelId != null
                ? salaryRepository.findByFarmIdAndPersonnelId(farmId, personnelId, pageable)
                : salaryRepository.findByFarmId(farmId, pageable);
        return page.map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public SalaryPaymentResponse get(UUID id, UUID farmId) {
        return mapper.toResponse(load(id, farmId));
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID farmId) {
        SalaryPayment sp = load(id, farmId);
        UUID expenseId = sp.getExpenseId();

        salaryRepository.delete(sp);

        if (expenseId != null) {
            expenseRepository.findById(expenseId).ifPresent(expenseRepository::delete);
        }

        log.info("Hard-deleted salary payment {} and linked expense {}", id, expenseId);
    }

    @Override
    @Transactional(readOnly = true)
    public SalaryReceiptResponse getReceipt(UUID id, UUID farmId) {
        SalaryPayment sp = load(id, farmId);
        Personnel p = personnelRepository.findById(sp.getPersonnelId())
                .orElseThrow(() -> new PersonnelNotFoundException(
                        "Personnel not found with ID: " + sp.getPersonnelId()));

        Farm farm = farmRepository.findById(farmId).orElse(null);
        String farmName = farm != null ? farm.getName() : "Agriva Farm";

        String managerName = "Farm Manager";
        if (farm != null && farm.getMasterId() != null) {
            Personnel masterPersonnel = personnelRepository
                    .findByUserId(farm.getMasterId()).orElse(null);
            if (masterPersonnel != null) managerName = masterPersonnel.getName();
        }

        Currency currency = Currency.XAF;
        if (sp.getExpenseId() != null) {
            Currency fromExpense = expenseRepository.findById(sp.getExpenseId())
                    .map(Expense::getCurrency)
                    .orElse(null);
            if (fromExpense != null) currency = fromExpense;
        }

        LocalDate periodEnd   = sp.getPaymentDate();
        LocalDate periodStart = periodEnd.minusDays(30);

        List<Task> tasks = taskRepository.findByPersonnelIdAndStatusAndValidationTimeBetween(
                p.getId(),
                TaskStatus.COMPLETED,
                periodStart.atStartOfDay(),
                periodEnd.atTime(23, 59, 59));

        BigDecimal expected = tasks.stream()
                .map(t -> t.getExpectedHours() != null
                        ? BigDecimal.valueOf(t.getExpectedHours()) : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal actual = tasks.stream()
                .map(t -> t.getActualHours() != null
                        ? BigDecimal.valueOf(t.getActualHours()) : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal rate = expected.compareTo(BigDecimal.ZERO) > 0
                ? actual.divide(expected, 4, RoundingMode.HALF_UP)
                  .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        List<SalaryReceiptLineItem> lines = tasks.stream().map(t -> {
            BigDecimal e = t.getExpectedHours() != null
                    ? BigDecimal.valueOf(t.getExpectedHours()) : BigDecimal.ZERO;
            BigDecimal a = t.getActualHours() != null
                    ? BigDecimal.valueOf(t.getActualHours()) : BigDecimal.ZERO;
            BigDecimal cr = e.compareTo(BigDecimal.ZERO) > 0
                    ? a.divide(e, 4, RoundingMode.HALF_UP)
                      .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            return SalaryReceiptLineItem.builder()
                    .title(t.getTitle())
                    .expectedHours(e)
                    .actualHours(a)
                    .completionRate(cr)
                    .status(t.getStatus().name())
                    .build();
        }).toList();

        return SalaryReceiptResponse.builder()
                .receiptNumber("RCPT-" + sp.getPaymentReference())
                .issueDate(LocalDate.now())
                .farmName(farmName)
                .managerName(managerName)
                .personnelName(p.getName())
                .personnelNationalId(p.getNationalId())
                .department(p.getDepartment() != null ? p.getDepartment().name() : null)
                .employmentDate(p.getDateOfEmployment())
                .amount(sp.getAmount())
                .currency(currency)
                .paymentDate(sp.getPaymentDate())
                .paymentReference(sp.getPaymentReference())
                .status(sp.getStatus().name())
                .tasks(lines)
                .totalExpectedHours(expected)
                .totalActualHours(actual)
                .overallProductivity(rate)
                .workingDays(p.getWorkingDaysCount())
                .periodStartDate(periodStart)
                .periodEndDate(periodEnd)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getReceiptPdf(UUID id, UUID farmId) {
        return pdfReceiptService.generate(getReceipt(id, farmId));
    }

    @Override
    public SalaryBulkUploadResponse bulkUpload(MultipartFile file, UUID farmId,
                                               UUID userId, String email) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required");
        }
        List<SalaryExcelParser.ParsedRow> rows;
        try { rows = excelParser.parse(file.getInputStream(), farmId); }
        catch (Exception e) {
            throw new IllegalArgumentException("Failed to read Excel file: " + e.getMessage(), e);
        }

        SalaryBulkUploadResponse response = new SalaryBulkUploadResponse();
        response.setTotalRows(rows.size());

        for (SalaryExcelParser.ParsedRow row : rows) {
            if (row.isError()) {
                response.setFailed(response.getFailed() + 1);
                response.getResults().add(new SalaryBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", row.error()));
                continue;
            }
            UUID submissionId = UUID.randomUUID();
            try {
                String payload = objectMapper.writeValueAsString(row.request());
                salaryProducer.publish(new SalarySubmissionEvent(
                        submissionId, farmId, userId, email, payload, LocalDateTime.now()));
                response.setQueued(response.getQueued() + 1);
                response.getResults().add(new SalaryBulkUploadResponse.RowResult(
                        row.rowNumber(), submissionId, "QUEUED", null));
            } catch (Exception e) {
                response.setFailed(response.getFailed() + 1);
                response.getResults().add(new SalaryBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", e.getMessage()));
            }
        }

        log.info("Bulk salary upload for farm {}: total={}, queued={}, failed={}",
                farmId, response.getTotalRows(), response.getQueued(), response.getFailed());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StagedSalaryResponse> listStaging(UUID farmId, String status, Pageable pageable) {
        Page<StagedSalary> page;
        if (status != null && !status.isBlank()) {
            page = stagedRepo.findByFarmIdAndStatus(
                    farmId, StagedSalaryStatus.valueOf(status.toUpperCase()), pageable);
        } else {
            page = stagedRepo.findByFarmId(farmId, pageable);
        }
        return page.map(s -> new StagedSalaryResponse(
                s.getId(), s.getFarmId(), s.getStatus().name(),
                s.getValidationErrors(), s.getPayloadJson(),
                s.getSubmittedByUserId(), s.getSubmittedByEmail(),
                s.getSubmittedAt(), s.getLastAttemptAt(), s.getNextRetryAt(),
                s.getProcessedAt(), s.getMovedSalaryId(), s.getRetryCount()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> stagingStats(UUID farmId) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (StagedSalaryStatus s : StagedSalaryStatus.values()) {
            out.put(s.name(), stagedRepo.countByFarmIdAndStatus(farmId, s));
        }
        return out;
    }

    private SalaryPayment load(UUID id, UUID farmId) {
        return salaryRepository.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new SalaryPaymentNotFoundException(
                        "Salary payment not found with ID: " + id));
    }

    private static boolean isUniqueReferenceViolation(DataIntegrityViolationException e) {
        String msg = e.getMostSpecificCause().getMessage();
        return msg != null && msg.contains("ux_salary_payments_ref");
    }
}