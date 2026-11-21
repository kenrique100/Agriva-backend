package com.agriva.dashboard.repository;

import com.agriva.finance.repository.ExpenseRepository;
import com.agriva.finance.repository.IncomeRepository;
import com.agriva.finance.repository.InvestmentRepository;
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
            return null; // Mixed currency
        }

        Object sumResult = sumsByCurrency.get(0)[1];
        if (sumResult == null) return BigDecimal.ZERO;

        // Safely convert from any Number type (BigDecimal, Double, Long) to BigDecimal
        if (sumResult instanceof BigDecimal bd) {
            return bd;
        } else if (sumResult instanceof Number num) {
            return new BigDecimal(num.toString());
        }
        return BigDecimal.ZERO;
    }
}