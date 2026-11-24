package com.agriva.finance.utils;

import com.agriva.finance.model.dto.request.ExpenseRequest;
import com.agriva.finance.repository.ExpenseRepository;
import com.agriva.shared.utils.AbstractBatchValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ExpenseValidator extends AbstractBatchValidator<ExpenseRequest> {

    private final ExpenseRepository expenseRepository;

    @Override protected int getQuantity(ExpenseRequest r)          { return r.getQtyPurchased(); }
    @Override protected BigDecimal getAmount(ExpenseRequest r)     { return r.getAmountPaid(); }
    @Override protected BigDecimal getExpectedAmount(ExpenseRequest r) { return r.getExpectedAmount(); }
    @Override protected LocalDate getDate(ExpenseRequest r)        { return r.getExpenseDate(); }
    @Override protected String getReason(ExpenseRequest r)         { return r.getReason(); }

    @Override
    protected boolean duplicateExists(UUID farmId, BigDecimal amount, LocalDate date, String reason) {
        return expenseRepository.existsByFarmIdAndAmountPaidAndExpenseDateAndReasonLive(
                farmId, amount, date, reason);
    }

    @Override protected String amountLabel() { return "Amount paid"; }
    @Override protected String dateLabel()   { return "Expense date"; }
    @Override protected String domainLabel() { return "expense"; }
}