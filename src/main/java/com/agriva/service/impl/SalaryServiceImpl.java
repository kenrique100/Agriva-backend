package com.agriva.service.impl;

import com.agriva.exception.*;
import com.agriva.mapper.SalaryMapper;
import com.agriva.model.dto.expense.ExpenseRequest;
import com.agriva.model.dto.expense.ExpenseResponse;
import com.agriva.model.dto.salary.*;
import com.agriva.model.entity.*;
import com.agriva.repository.*;
import com.agriva.service.ExpenseService;
import com.agriva.service.PdfReceiptService;
import com.agriva.service.SalaryService;
import com.agriva.utils.SalaryValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
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
    private final ExpenseService expenseService;
    private final SalaryValidator validator;
    private final SalaryMapper mapper;
    private final PdfReceiptService pdfReceiptService;

    @Override
    @Transactional
    public SalaryPaymentResponse create(SalaryPaymentRequest req, UUID farmId, UUID actorUserId) {
        Personnel p = personnelRepository.findByIdAndFarmId(req.getPersonnelId(), farmId)
                .orElseThrow(() -> new PersonnelNotFoundException(
                        "Personnel not found with ID: " + req.getPersonnelId()));
        validator.validate(p);

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
                .personnel(p)
                .amount(req.getAmount())
                .paymentDate(req.getPaymentDate())
                .status(SalaryPaymentStatus.PROCESSED)
                .paymentReference(generateReference())
                .expenseId(expense.getId())
                .paidByUserId(actorUserId)
                .build();

        SalaryPayment saved = salaryRepository.save(sp);
        log.info("Salary {} paid for personnel {} — expense {}",
                saved.getId(), p.getId(), expense.getId());
        return mapper.toResponse(saved);
    }

    private String generateReference() {
        String candidate;
        int attempt = 0;
        do {
            String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            candidate = "SAL-" + LocalDate.now().getYear() + "-" + suffix;
            attempt++;
        } while (salaryRepository.existsByPaymentReference(candidate) && attempt < 10);
        return candidate;
    }

    /**
     * FIX: readOnly=true keeps the Hibernate Session open while
     * SalaryMapper.toResponse() reads SalaryPayment.personnel (lazy proxy).
     * Without this, open-in-view=false closes the session → LazyInitializationException.
     */
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
        if (sp.getExpenseId() != null) {
            expenseRepository.findById(sp.getExpenseId()).ifPresent(expenseRepository::delete);
        }
        salaryRepository.delete(sp);
    }

    @Override
    @Transactional(readOnly = true)
    public SalaryReceiptResponse getReceipt(UUID id, UUID farmId) {
        SalaryPayment sp = load(id, farmId);
        Personnel p = sp.getPersonnel();

        List<Task> tasks = taskRepository.findByPersonnelIdAndStatusAndValidationTimeBetween(
                p.getId(),
                TaskStatus.COMPLETED,
                sp.getPaymentDate().atStartOfDay().minusDays(30),
                sp.getPaymentDate().atTime(23, 59, 59));

        BigDecimal expected = tasks.stream()
                .map(t -> t.getExpectedHours() != null
                        ? BigDecimal.valueOf(t.getExpectedHours()) : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal actual = tasks.stream()
                .map(t -> t.getActualHours() != null
                        ? BigDecimal.valueOf(t.getActualHours()) : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal rate = expected.compareTo(BigDecimal.ZERO) > 0
                ? actual.divide(expected, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        List<SalaryReceiptLineItem> lines = tasks.stream().map(t -> {
            BigDecimal e = t.getExpectedHours() != null
                    ? BigDecimal.valueOf(t.getExpectedHours()) : BigDecimal.ZERO;
            BigDecimal a = t.getActualHours() != null
                    ? BigDecimal.valueOf(t.getActualHours()) : BigDecimal.ZERO;
            BigDecimal cr = e.compareTo(BigDecimal.ZERO) > 0
                    ? a.divide(e, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
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
                .personnelName(p.getName())
                .personnelNationalId(p.getNationalId())
                .department(p.getDepartment() != null ? p.getDepartment().name() : null)
                .employmentDate(p.getDateOfEmployment())
                .amount(sp.getAmount())
                .paymentDate(sp.getPaymentDate())
                .paymentReference(sp.getPaymentReference())
                .status(sp.getStatus().name())
                .tasks(lines)
                .totalExpectedHours(expected)
                .totalActualHours(actual)
                .overallProductivity(rate)
                .workingDays(p.getWorkingDaysCount())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getReceiptPdf(UUID id, UUID farmId) {
        return pdfReceiptService.generate(getReceipt(id, farmId));
    }

    private SalaryPayment load(UUID id, UUID farmId) {
        return salaryRepository.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new SalaryPaymentNotFoundException(
                        "Salary payment not found with ID: " + id));
    }
}