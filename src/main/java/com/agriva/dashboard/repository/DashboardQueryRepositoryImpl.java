package com.agriva.dashboard.repository;

import com.agriva.finance.repository.ExpenseRepository;
import com.agriva.finance.repository.IncomeRepository;
import com.agriva.finance.repository.InvestmentRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
public class DashboardQueryRepositoryImpl implements DashboardQueryRepository {

    private final IncomeRepository incomeRepo;
    private final ExpenseRepository expenseRepo;
    private final InvestmentRepository investmentRepo;
    private final EntityManager em;

    public DashboardQueryRepositoryImpl(IncomeRepository incomeRepo,
                                        ExpenseRepository expenseRepo,
                                        InvestmentRepository investmentRepo,
                                        EntityManager em) {
        this.incomeRepo = incomeRepo;
        this.expenseRepo = expenseRepo;
        this.investmentRepo = investmentRepo;
        this.em = em;
    }

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

    @Override
    public Map<UUID, Totals> totalsByUserForFarm(UUID farmId) {
        // Three grouped queries (one per entity) instead of 6 per user.
        // Shape: [created_by_user_id, currency, count(*), sum(amount)].
        Map<UUID, List<Object[]>> incomeByUser = groupByUser(aggregateByUser(
                "SELECT created_by_user_id, currency, COUNT(*), SUM(amount_received) " +
                        "FROM income " +
                        "WHERE farm_id = :farmId AND deleted_at IS NULL " +
                        "  AND created_by_user_id IS NOT NULL " +
                        "GROUP BY created_by_user_id, currency",
                farmId));

        Map<UUID, List<Object[]>> expenseByUser = groupByUser(aggregateByUser(
                "SELECT created_by_user_id, currency, COUNT(*), SUM(amount_paid) " +
                        "FROM expense " +
                        "WHERE farm_id = :farmId AND deleted_at IS NULL " +
                        "  AND created_by_user_id IS NOT NULL " +
                        "GROUP BY created_by_user_id, currency",
                farmId));

        Map<UUID, List<Object[]>> investmentByUser = groupByUser(aggregateByUser(
                "SELECT created_by_user_id, currency, COUNT(*), SUM(amount) " +
                        "FROM investment " +
                        "WHERE farm_id = :farmId AND deleted_at IS NULL " +
                        "  AND created_by_user_id IS NOT NULL " +
                        "GROUP BY created_by_user_id, currency",
                farmId));

        Set<UUID> allUserIds = new HashSet<>();
        allUserIds.addAll(incomeByUser.keySet());
        allUserIds.addAll(expenseByUser.keySet());
        allUserIds.addAll(investmentByUser.keySet());

        Map<UUID, Totals> result = new HashMap<>(allUserIds.size());
        for (UUID userId : allUserIds) {
            result.put(userId, new Totals(
                    sumCounts(incomeByUser.get(userId)),
                    sumCounts(expenseByUser.get(userId)),
                    sumCounts(investmentByUser.get(userId)),
                    sumSingleCurrencyOrNullGrouped(incomeByUser.get(userId)),
                    sumSingleCurrencyOrNullGrouped(expenseByUser.get(userId)),
                    sumSingleCurrencyOrNullGrouped(investmentByUser.get(userId))
            ));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> aggregateByUser(String sql, UUID farmId) {
        return em.createNativeQuery(sql)
                .setParameter("farmId", farmId)
                .getResultList();
    }

    private static Map<UUID, List<Object[]>> groupByUser(List<Object[]> rows) {
        Map<UUID, List<Object[]>> byUser = new HashMap<>();
        for (Object[] row : rows) {
            UUID userId = toUuid(row[0]);
            if (userId == null) continue;
            String currency = row[1] == null ? null : row[1].toString();
            Object count = row[2];
            Object sum = row[3];
            // Re-shape to [currency, count, sum] for the same downstream helpers.
            byUser.computeIfAbsent(userId, k -> new ArrayList<>())
                    .add(new Object[]{currency, count, sum});
        }
        return byUser;
    }

    private static long sumCounts(List<Object[]> rows) {
        if (rows == null) return 0L;
        long total = 0L;
        for (Object[] row : rows) {
            Object c = row[1];
            if (c instanceof Number n) total += n.longValue();
        }
        return total;
    }

    private static BigDecimal sumSingleCurrencyOrNullGrouped(List<Object[]> rows) {
        if (rows == null || rows.isEmpty()) return BigDecimal.ZERO;
        if (rows.size() > 1) return null;                // multiple currencies
        Object v = rows.get(0)[2];
        return toBigDecimal(v);
    }

    /** Kept from the original implementation for farm/user totals. */
    private BigDecimal sumSingleCurrencyOrNull(List<Object[]> sumsByCurrency) {
        if (sumsByCurrency == null || sumsByCurrency.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (sumsByCurrency.size() > 1) {
            return null;
        }
        Object sumResult = sumsByCurrency.get(0)[1];
        return toBigDecimal(sumResult);
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return new BigDecimal(n.toString());
        return BigDecimal.ZERO;
    }

    private static UUID toUuid(Object v) {
        if (v == null) return null;
        if (v instanceof UUID u) return u;
        try {
            return UUID.fromString(v.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}