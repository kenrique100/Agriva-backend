package com.agriva.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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
                incomeRepo.sumAmountByFarmIdOrZero(farmId),
                expenseRepo.sumAmountByFarmIdOrZero(farmId),
                investmentRepo.sumAmountByFarmIdOrZero(farmId)
        );
    }

    @Override
    public Totals totalsForUser(UUID farmId, UUID userId) {
        return new Totals(
                incomeRepo.countByFarmIdAndCreatedByUserId(farmId, userId),
                expenseRepo.countByFarmIdAndCreatedByUserId(farmId, userId),
                investmentRepo.countByFarmIdAndCreatedByUserId(farmId, userId),
                incomeRepo.sumAmountByFarmIdAndUserOrZero(farmId, userId),
                expenseRepo.sumAmountByFarmIdAndUserOrZero(farmId, userId),
                investmentRepo.sumAmountByFarmIdAndUserOrZero(farmId, userId)
        );
    }
}