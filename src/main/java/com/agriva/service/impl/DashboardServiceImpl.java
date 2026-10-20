package com.agriva.service.impl;

import com.agriva.model.dto.dashboard.DashboardResponse;
import com.agriva.model.entity.Expense;
import com.agriva.model.entity.Farm;
import com.agriva.model.entity.Income;
import com.agriva.model.entity.Investment;
import com.agriva.model.entity.User;
import com.agriva.repository.AuthRepository;
import com.agriva.repository.DashboardQueryRepository;
import com.agriva.repository.ExpenseRepository;
import com.agriva.repository.FarmRepository;
import com.agriva.repository.IncomeRepository;
import com.agriva.repository.InvestmentRepository;
import com.agriva.repository.TransactionRepository;
import com.agriva.security.RoleConstants;
import com.agriva.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private final DashboardQueryRepository dashboardQueryRepo;
    private final FarmRepository farmRepository;
    private final AuthRepository authRepository;
    private final IncomeRepository incomeRepo;
    private final ExpenseRepository expenseRepo;
    private final InvestmentRepository investmentRepo;
    private final TransactionRepository txRepo;

    @Override
    public DashboardResponse personalDashboard(UUID userId, UUID farmId, String role) {
        boolean canSeeInvestments = isMasterOrAdmin(role);

        User user = userId != null ? authRepository.findById(userId).orElse(null) : null;
        String farmName = farmId != null
                ? farmRepository.findById(farmId).map(Farm::getName).orElse(null)
                : null;

        DashboardQueryRepository.Totals personal = userId != null
                ? dashboardQueryRepo.totalsForUser(farmId, userId)
                : DashboardQueryRepository.Totals.empty();

        DashboardQueryRepository.Totals farm = farmId != null
                ? dashboardQueryRepo.totalsForFarm(farmId)
                : DashboardQueryRepository.Totals.empty();

        List<DashboardResponse.TransactionSummary> txSummaries =
                buildTransactionSummaries(farmId, canSeeInvestments);

        return DashboardResponse.builder()
                .farmId(farmId)
                .farmName(farmName)
                .userId(userId)
                .username(user != null ? user.getUsername() : null)
                .role(role)
                .personal(toTotals(personal, canSeeInvestments))
                .farm(toTotals(farm, canSeeInvestments))
                .transactions(txSummaries)
                .build();
    }

    @Override
    public DashboardResponse farmDashboard(UUID farmId, UUID requesterUserId) {
        User requester = requesterUserId != null
                ? authRepository.findById(requesterUserId).orElse(null)
                : null;

        String role = requester != null ? requester.getRole() : RoleConstants.MASTER;
        boolean canSeeInvestments = isMasterOrAdmin(role);

        String farmName = farmRepository.findById(farmId)
                .map(Farm::getName)
                .orElse(null);

        DashboardQueryRepository.Totals farmTotals = dashboardQueryRepo.totalsForFarm(farmId);

        List<User> farmUsers = authRepository.findByFarmId(farmId);

        List<DashboardResponse.UserActivity> userActivities = new ArrayList<>();
        for (User u : farmUsers) {
            DashboardQueryRepository.Totals userTotals =
                    dashboardQueryRepo.totalsForUser(farmId, u.getId());

            userActivities.add(DashboardResponse.UserActivity.builder()
                    .userId(u.getId())
                    .email(u.getEmail())
                    .username(u.getUsername())
                    .role(u.getRole())
                    .activitySummary(toTotals(userTotals, canSeeInvestments))
                    .build());
        }

        List<DashboardResponse.TransactionSummary> txSummaries =
                buildTransactionSummaries(farmId, canSeeInvestments);

        return DashboardResponse.builder()
                .farmId(farmId)
                .farmName(farmName)
                .userId(requesterUserId)
                .username(requester != null ? requester.getUsername() : null)
                .role(role)
                .farm(toTotals(farmTotals, canSeeInvestments))
                .users(userActivities)
                .transactions(txSummaries)
                .build();
    }

    private List<DashboardResponse.TransactionSummary> buildTransactionSummaries(
            UUID farmId, boolean canSeeInvestments) {

        List<DashboardResponse.TransactionSummary> out = new ArrayList<>();

        txRepo.findByFarmId(farmId, Pageable.unpaged()).forEach(tx -> {
            List<Income> incomes = incomeRepo.findByTransactionId(tx.getId());
            List<Expense> expenses = expenseRepo.findByTransactionId(tx.getId());
            List<Investment> investments = canSeeInvestments
                    ? investmentRepo.findByTransactionId(tx.getId())
                    : List.of();

            Set<UUID> creatorIds = new HashSet<>();
            incomes.forEach(i -> { if (i.getCreatedByUserId() != null) creatorIds.add(i.getCreatedByUserId()); });
            expenses.forEach(e -> { if (e.getCreatedByUserId() != null) creatorIds.add(e.getCreatedByUserId()); });
            investments.forEach(v -> { if (v.getCreatedByUserId() != null) creatorIds.add(v.getCreatedByUserId()); });
            if (tx.getCreatedByUserId() != null) creatorIds.add(tx.getCreatedByUserId());

            Map<UUID, String> usernameById = creatorIds.isEmpty()
                    ? Map.of()
                    : authRepository.findAllById(creatorIds).stream()
                      .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));

            BigDecimal totalIncome = incomes.stream()
                    .map(Income::getAmountReceived).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalExpense = expenses.stream()
                    .map(Expense::getAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalInvestment = canSeeInvestments
                    ? investments.stream().map(Investment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                    : null;

            BigDecimal netPosition = canSeeInvestments
                    ? totalIncome.subtract(totalExpense).subtract(totalInvestment)
                    : null;

            List<DashboardResponse.IncomeLine> incomeLines = incomes.stream()
                    .map(i -> DashboardResponse.IncomeLine.builder()
                            .id(i.getId())
                            .amount(i.getAmountReceived())
                            .currency(i.getCurrency().name())
                            .reason(i.getReason())
                            .category(i.getCategory().name())
                            .incomeDate(i.getIncomeDate())
                            .createdByUserId(i.getCreatedByUserId())
                            .createdByUsername(usernameOrNull(i.getCreatedByUserId(), usernameById))
                            .build())
                    .toList();

            List<DashboardResponse.ExpenseLine> expenseLines = expenses.stream()
                    .map(e -> DashboardResponse.ExpenseLine.builder()
                            .id(e.getId())
                            .amount(e.getAmountPaid())
                            .currency(e.getCurrency().name())
                            .reason(e.getReason())
                            .category(e.getCategory().name())
                            .expenseDate(e.getExpenseDate())
                            .createdByUserId(e.getCreatedByUserId())
                            .createdByUsername(usernameOrNull(e.getCreatedByUserId(), usernameById))
                            .build())
                    .toList();

            List<DashboardResponse.InvestmentLine> investmentLines = canSeeInvestments
                    ? investments.stream()
                      .map(v -> DashboardResponse.InvestmentLine.builder()
                                .id(v.getId())
                                .amount(v.getAmount())
                                .currency(v.getCurrency())
                                .investorName(v.getInvestorName())
                                .type(v.getType())
                                .description(v.getDescription())
                                .investmentDate(v.getInvestmentDate())
                                .createdByUserId(v.getCreatedByUserId())
                                .createdByUsername(usernameOrNull(v.getCreatedByUserId(), usernameById))
                                .build())
                      .toList()
                    : null;

            out.add(DashboardResponse.TransactionSummary.builder()
                    .id(tx.getId())
                    .transactionRef(tx.getTransactionRef())
                    .occurredAt(tx.getOccurredAt())
                    .status(tx.getStatus().name())
                    .notes(tx.getNotes())
                    .recordedByUserId(tx.getCreatedByUserId())
                    .recordedByUsername(usernameOrNull(tx.getCreatedByUserId(), usernameById))
                    .recordedByRole(tx.getCreatedByUserId() == null
                            ? null
                            : authRepository.findById(tx.getCreatedByUserId())
                              .map(User::getRole).orElse(null))
                    .income(totalIncome)
                    .expense(totalExpense)
                    .investment(totalInvestment)
                    .netPosition(netPosition)
                    .incomeCount(incomes.size())
                    .expenseCount(expenses.size())
                    .investmentCount(canSeeInvestments ? investments.size() : null)
                    .incomeLines(incomeLines.isEmpty() ? null : incomeLines)
                    .expenseLines(expenseLines.isEmpty() ? null : expenseLines)
                    .investmentLines(investmentLines == null || investmentLines.isEmpty() ? null : investmentLines)
                    .build());
        });

        return out;
    }

    private DashboardResponse.Totals toTotals(DashboardQueryRepository.Totals src,
                                              boolean canSeeInvestments) {
        if (src == null) return null;

        BigDecimal netPosition = canSeeInvestments
                ? src.totalIncome().subtract(src.totalExpense()).subtract(src.totalInvestment())
                : null;

        BigDecimal netGain = null;
        BigDecimal netLoss = null;
        if (canSeeInvestments) {
            netGain = netPosition.signum() > 0 ? netPosition : BigDecimal.ZERO;
            netLoss = netPosition.signum() < 0 ? netPosition.abs() : BigDecimal.ZERO;
        }

        return DashboardResponse.Totals.builder()
                .totalTransactions(
                        canSeeInvestments
                                ? src.incomeCount() + src.expenseCount() + src.investmentCount()
                                : src.incomeCount() + src.expenseCount())
                .totalIncomes(src.incomeCount())
                .totalExpenses(src.expenseCount())
                .totalInvestments(canSeeInvestments ? src.investmentCount() : null)
                .totalIncome(src.totalIncome())
                .totalExpense(src.totalExpense())
                .totalInvestment(canSeeInvestments ? src.totalInvestment() : null)
                .netGain(netGain)
                .netLoss(netLoss)
                .netPosition(netPosition)
                .build();
    }

    private String usernameOrNull(UUID userId, Map<UUID, String> usernameById) {
        return userId == null ? null : usernameById.getOrDefault(userId, "deleted-user");
    }

    private boolean isMasterOrAdmin(String role) {
        return RoleConstants.MASTER.equals(role) || RoleConstants.ADMIN.equals(role);
    }
}