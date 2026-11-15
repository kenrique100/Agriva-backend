package com.agriva.farming.mapper;

import com.agriva.farming.model.dto.response.FarmDetailsResponse;
import com.agriva.farming.model.dto.response.FarmListResponse;
import com.agriva.farming.model.entity.Farm;
import com.agriva.shared.models.ref.UserRef;
import com.agriva.shared.security.RoleConstants;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class FarmMapper {

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    public FarmDetailsResponse toDetails(Farm farm,
                                         List<UserRef> users,
                                         FarmTotals totals,
                                         Map<UUID, UserActivity> activityByUser) {

        int masters = 0;
        int subUsers = 0;
        for (UserRef u : users) {
            if (RoleConstants.MASTER.equals(u.role())) masters++;
            else if (RoleConstants.SUB_USER.equals(u.role())) subUsers++;
        }

        List<FarmDetailsResponse.FarmUser> userViews = new ArrayList<>();
        for (UserRef u : users) {
            UserActivity activity = activityByUser == null ? null : activityByUser.get(u.id());

            FarmDetailsResponse.UserActivity activityDto = activity == null ? null
                    : FarmDetailsResponse.UserActivity.builder()
                      .totalIncomes(activity.incomes())
                      .totalExpenses(activity.expenses())
                      .totalInvestments(activity.investments())
                      .totalIncome(activity.income())
                      .totalExpense(activity.expense())
                      .totalInvestment(activity.investment())
                      .netPosition(nz(activity.income())
                                   .subtract(nz(activity.expense()))
                                   .subtract(nz(activity.investment())))
                      .build();

            userViews.add(FarmDetailsResponse.FarmUser.builder()
                    .userId(u.id())
                    .email(u.email())
                    .username(u.username())
                    .role(u.role())
                    .createdAt(u.createdAt())
                    .updatedAt(u.updatedAt())
                    .activitySummary(activityDto)
                    .build());
        }

        BigDecimal income = nz(totals.income());
        BigDecimal expense = nz(totals.expense());
        BigDecimal investment = nz(totals.investment());
        BigDecimal netPosition = income.subtract(expense).subtract(investment);
        BigDecimal netGain = netPosition.signum() > 0 ? netPosition : BigDecimal.ZERO;
        BigDecimal netLoss = netPosition.signum() < 0 ? netPosition.abs() : BigDecimal.ZERO;

        return FarmDetailsResponse.builder()
                .farmId(farm.getId())
                .farmName(farm.getName())
                .masterId(farm.getMasterId())
                .createdAt(farm.getCreatedAt())
                .updatedAt(farm.getUpdatedAt())
                .totalUsers(users.size())
                .totalMasters(masters)
                .totalSubUsers(subUsers)
                .totalIncomes(totals.incomeCount())
                .totalIncomeAmount(totals.income())
                .totalExpenses(totals.expenseCount())
                .totalExpenseAmount(totals.expense())
                .totalInvestments(totals.investmentCount())
                .totalInvestmentAmount(totals.investment())
                .totalTransactions(totals.transactionCount())
                .netGain(netGain)
                .netLoss(netLoss)
                .netPosition(netPosition)
                .users(userViews)
                .build();
    }

    public FarmListResponse toList(List<FarmDetailsResponse> details) {
        int totalUsers = 0, totalMasters = 0, totalSubUsers = 0;
        long totalTransactions = 0;
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        BigDecimal investment = BigDecimal.ZERO;

        for (FarmDetailsResponse d : details) {
            totalUsers += d.getTotalUsers() == null ? 0 : d.getTotalUsers();
            totalMasters += d.getTotalMasters() == null ? 0 : d.getTotalMasters();
            totalSubUsers += d.getTotalSubUsers() == null ? 0 : d.getTotalSubUsers();
            totalTransactions += d.getTotalTransactions() == null ? 0 : d.getTotalTransactions();
            income = income.add(nz(d.getTotalIncomeAmount()));
            expense = expense.add(nz(d.getTotalExpenseAmount()));
            investment = investment.add(nz(d.getTotalInvestmentAmount()));
        }

        return FarmListResponse.builder()
                .totalFarms(details.size())
                .totalUsers(totalUsers)
                .totalMasters(totalMasters)
                .totalSubUsers(totalSubUsers)
                .totalTransactions(totalTransactions)
                .grandTotalIncome(income)
                .grandTotalExpense(expense)
                .grandTotalInvestment(investment)
                .grandNetPosition(income.subtract(expense).subtract(investment))
                .farms(details)
                .build();
    }

    public record FarmTotals(
            long incomeCount, long expenseCount, long investmentCount, long transactionCount,
            BigDecimal income, BigDecimal expense, BigDecimal investment
    ) {}

    public record UserActivity(
            long incomes, long expenses, long investments,
            BigDecimal income, BigDecimal expense, BigDecimal investment
    ) {}
}