package com.agriva.finance.utils;

import com.agriva.finance.model.dto.request.IncomeRequest;
import com.agriva.finance.repository.IncomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IncomeValidator extends AbstractBatchValidator<IncomeRequest> {

    private final IncomeRepository incomeRepository;

    @Override protected int getQuantity(IncomeRequest r)          { return r.getQuantity(); }
    @Override protected BigDecimal getAmount(IncomeRequest r)     { return r.getAmountReceived(); }
    @Override protected BigDecimal getExpectedAmount(IncomeRequest r) { return r.getExpectedAmount(); }
    @Override protected LocalDate getDate(IncomeRequest r)        { return r.getIncomeDate(); }
    @Override protected String getReason(IncomeRequest r)         { return r.getReason(); }

    @Override
    protected boolean duplicateExists(UUID farmId, BigDecimal amount, LocalDate date, String reason) {
        return incomeRepository.existsByFarmIdAndAmountReceivedAndIncomeDateAndReasonLive(
                farmId, amount, date, reason);
    }

    @Override protected String amountLabel() { return "Amount received"; }
    @Override protected String dateLabel()   { return "Income date"; }
    @Override protected String domainLabel() { return "income"; }
}