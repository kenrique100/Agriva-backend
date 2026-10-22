package com.agriva.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface DashboardQueryRepository {

    Totals totalsForFarm(UUID farmId);

    Totals totalsForUser(UUID farmId, UUID userId);

    record Totals(
            long incomeCount,
            long expenseCount,
            long investmentCount,
            BigDecimal totalIncome,
            BigDecimal totalExpense,
            BigDecimal totalInvestment
    ) {
        public static Totals empty() {
            return new Totals(0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }
}