package com.agriva.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class DashboardQueryRepositoryImpl implements DashboardQueryRepository {

    private final IncomeRepository incomeRepo;
    private final ExpenseRepository expenseRepo;
    private final InvestmentRepository investmentRepo;

    @Override
    public Totals totalsForFarm(UUID farmId) {
        return new Totals(
                incomeRepo.countByFarmId(farmId),
                expenseRepo.countByFarmId(farmId),
                investmentRepo.countByFarmId(farmId),
                sumSingleCurrencyOrNull(incomeRepo.sumAmountGroupedByCurrency(farmId)),
                sumSingleCurrencyOrNull(expenseRepo.sumAmountGroupedByCurrency(farmId)),
                sumSingleCurrencyOrNull(investmentRepo.sumAmountGroupedByCurrency(farmId))
        );
    }

    @Override
    public Totals totalsForUser(UUID farmId, UUID userId) {
        return new Totals(
                incomeRepo.countByFarmIdAndCreatedByUserId(farmId, userId),
                expenseRepo.countByFarmIdAndCreatedByUserId(farmId, userId),
                investmentRepo.countByFarmIdAndCreatedByUserId(farmId, userId),
                sumSingleCurrencyOrNull(incomeRepo.sumAmountGroupedByCurrencyAndCreatedByUserId(farmId, userId)),
                sumSingleCurrencyOrNull(expenseRepo.sumAmountGroupedByCurrencyAndCreatedByUserId(farmId, userId)),
                sumSingleCurrencyOrNull(investmentRepo.sumAmountGroupedByCurrencyAndCreatedByUserId(farmId, userId))
        );
    }

    private BigDecimal sumSingleCurrencyOrNull(List<Object[]> sumsByCurrency) {
        if (sumsByCurrency == null || sumsByCurrency.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (sumsByCurrency.size() > 1) {
            return null;
        }
        return (BigDecimal) sumsByCurrency.get(0)[1];
    }
}
