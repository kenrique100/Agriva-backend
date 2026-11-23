package com.agriva.finance.scheduler;

import com.agriva.finance.model.dto.request.ExpenseRequest;
import com.agriva.finance.model.entity.Expense;
import com.agriva.finance.model.entity.Investment;
import com.agriva.finance.model.entity.StagedExpense;
import com.agriva.shared.models.enums.StagedStatus;
import com.agriva.finance.repository.ExpenseRepository;
import com.agriva.finance.repository.InvestmentRepository;
import com.agriva.finance.repository.StagedExpenseRepository;
import com.agriva.finance.utils.ExpenseValidator;
import com.agriva.shared.lock.DistributedLockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class StagedExpenseValidator extends AbstractStagedValidator<StagedExpense, ExpenseRequest> {

    private final StagedExpenseRepository stagedRepo;
    private final ExpenseRepository expenseRepo;
    private final InvestmentRepository investmentRepo;
    private final ExpenseValidator expenseValidator;

    @Value("${agriva.expense.validator.batch-size:100}")          private int batchSize;
    @Value("${agriva.expense.validator.max-attempts:4}")          private int maxAttempts;
    @Value("${agriva.expense.validator.retry-delay-minutes:1}")   private long retryDelayMinutes;

    public StagedExpenseValidator(ObjectMapper objectMapper,
                                  TransactionTemplate transactionTemplate,
                                  DistributedLockService lock,
                                  StagedExpenseRepository stagedRepo,
                                  ExpenseRepository expenseRepo,
                                  InvestmentRepository investmentRepo,
                                  ExpenseValidator expenseValidator) {
        super(objectMapper, transactionTemplate, lock);
        this.stagedRepo = stagedRepo;
        this.expenseRepo = expenseRepo;
        this.investmentRepo = investmentRepo;
        this.expenseValidator = expenseValidator;
    }

    @Scheduled(cron = "${agriva.expense.validator.cron:0 * * * * *}")
    public void processStagedExpenses() {
        process();
    }

    @Override protected int  getBatchSize()          { return batchSize; }
    @Override protected int  getMaxAttempts()        { return maxAttempts; }
    @Override protected long getRetryDelayMinutes()  { return retryDelayMinutes; }

    @Override protected String typeName()   { return "expense"; }
    @Override protected String getLockKey() { return "staged-expense-validator"; }

    @Override protected Class<ExpenseRequest> requestType() { return ExpenseRequest.class; }

    @Override
    protected List<String> validateRequest(ExpenseRequest request, UUID farmId) {
        // Allow negative amounts for reversals/credits
        if (request.getAmountPaid() != null && request.getAmountPaid().signum() < 0) {
            return List.of();
        }
        return expenseValidator.validate(request, farmId);
    }

    @Override
    protected List<StagedExpense> fetchDueRows(int batchSize) {
        return stagedRepo.findDueForProcessing(
                LocalDateTime.now(), StagedStatus.PENDING, StagedStatus.RETRYING,
                PageRequest.of(0, batchSize));
    }

    @Override protected StagedExpense findForUpdate(UUID stagedId) {
        return stagedRepo.findByIdForUpdate(stagedId).orElse(null);
    }

    @Override protected void saveStaged(StagedExpense staged) {
        stagedRepo.save(staged);
    }

    @Override protected void setMovedReference(StagedExpense staged, UUID movedId) {
        staged.setMovedExpenseId(movedId);
    }

    @Override
    protected PromotionResult promote(StagedExpense staged, ExpenseRequest request) {
        Investment active = investmentRepo.findActiveForUpdate(staged.getFarmId())
                .orElse(null);
        if (active == null) {
            return PromotionResult.terminal(
                    "No active investment for this farm. Create and activate an investment before recording expenses.");
        }

        BigDecimal alreadySpent = investmentRepo.sumExpensesByInvestmentIdOrZero(active.getId());
        BigDecimal projected = alreadySpent.add(request.getAmountPaid());

        if (projected.compareTo(active.getAmount()) > 0) {
            BigDecimal remaining = active.getAmount().subtract(alreadySpent);
            return PromotionResult.terminal(String.format(
                    "Investment limit exceeded. Remaining balance: %s %s. Expense amount paid: %s %s.",
                    remaining.toPlainString(), active.getCurrency(),
                    request.getAmountPaid().toPlainString(), request.getCurrency()));
        }

        Expense expense = Expense.builder()
                .farmId(staged.getFarmId())
                .transactionId(request.getTransactionId())
                .investmentId(active.getId())
                .reason(request.getReason())
                .qtyPurchased(request.getQtyPurchased())
                .amountPaid(request.getAmountPaid())
                .expectedAmount(request.getExpectedAmount())
                .receipt(request.getReceipt())
                .currency(request.getCurrency())
                .category(request.getCategory())
                .expenseDate(request.getExpenseDate())
                .createdByUserId(staged.getSubmittedByUserId())
                .build();
        expense.calculateDueBalance();
        Expense saved = expenseRepo.save(expense);
        return PromotionResult.moved(saved.getId());
    }
}