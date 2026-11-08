package com.agriva.dashboard.service.impl;

import com.agriva.dashboard.model.DashboardProductivityResponse;
import com.agriva.dashboard.model.DashboardResponse;
import com.agriva.finance.model.entity.Expense;
import com.agriva.farming.model.entity.Farm;
import com.agriva.finance.model.entity.Income;
import com.agriva.finance.model.entity.Investment;
import com.agriva.personnel.models.entity.Personnel;
import com.agriva.task.model.entity.Task;
import com.agriva.finance.model.entity.Transaction;
import com.agriva.identity.models.entity.User;
import com.agriva.identity.repository.AuthRepository;
import com.agriva.dashboard.repository.DashboardQueryRepository;
import com.agriva.finance.repository.ExpenseRepository;
import com.agriva.farming.repository.FarmRepository;
import com.agriva.finance.repository.IncomeRepository;
import com.agriva.finance.repository.InvestmentRepository;
import com.agriva.personnel.repository.PersonnelRepository;
import com.agriva.task.repository.TaskRepository;
import com.agriva.finance.repository.TransactionRepository;
import com.agriva.shared.security.RoleConstants;
import com.agriva.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private static final int DASHBOARD_TX_LIMIT = 50;

    private final DashboardQueryRepository dashboardQueryRepo;
    private final FarmRepository farmRepository;
    private final AuthRepository authRepository;
    private final IncomeRepository incomeRepo;
    private final ExpenseRepository expenseRepo;
    private final InvestmentRepository investmentRepo;
    private final TransactionRepository txRepo;
    private final PersonnelRepository personnelRepository;
    private final TaskRepository taskRepository;

    @Override
    public DashboardResponse personalDashboard(UUID userId, UUID farmId, String role) {
        boolean canSeeInvestments = isMasterOrAdmin(role);

        User user = userId != null ? authRepository.findByIdLive(userId).orElse(null) : null;
        String farmName = farmId != null
                ? farmRepository.findByIdLive(farmId).map(Farm::getName).orElse(null)
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
                ? authRepository.findByIdLive(requesterUserId).orElse(null)
                : null;

        String role = requester != null ? requester.getRole() : RoleConstants.MASTER;
        boolean canSeeInvestments = isMasterOrAdmin(role);

        String farmName = farmRepository.findByIdLive(farmId)
                .map(Farm::getName).orElse(null);

        DashboardQueryRepository.Totals farmTotals = dashboardQueryRepo.totalsForFarm(farmId);

        List<User> farmUsers = authRepository.findByFarmIdLive(farmId);

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

    @Override
    @Transactional(readOnly = true)
    public DashboardProductivityResponse productivity(UUID userId, UUID farmId, String role) {
        boolean isMaster = isMasterOrAdmin(role);
        List<DashboardProductivityResponse.UserProductivity> rows = new ArrayList<>();

        if (isMaster) {
            Map<UUID, double[]> aggregates = new HashMap<>();
            for (Object[] r : taskRepository.aggregateProductivityByFarm(farmId)) {
                UUID pid = (UUID) r[0];
                aggregates.put(pid, new double[]{
                        ((Number) r[1]).doubleValue(),
                        ((Number) r[2]).doubleValue()
                });
            }
            for (Personnel p : personnelRepository
                    .findByFarmIdLive(farmId, Pageable.unpaged()).getContent()) {
                double[] a = aggregates.getOrDefault(p.getId(), new double[]{0, 0});
                rows.add(toProductivity(p, a[0], a[1]));
            }
        } else {
            personnelRepository.findByUserIdLive(userId).ifPresent(p -> {
                List<Task> tasks = taskRepository
                        .findByPersonnelIdAndIsValidatedLive(p.getId(), true);
                double hours = tasks.stream()
                        .mapToDouble(t -> t.getActualHours() != null ? t.getActualHours() : 0.0)
                        .sum();
                double expected = tasks.stream()
                        .mapToDouble(t -> t.getExpectedHours() != null ? t.getExpectedHours() : 0)
                        .sum();
                rows.add(toProductivity(p, hours, expected));
            });
        }
        return DashboardProductivityResponse.builder().users(rows).build();
    }

    private DashboardProductivityResponse.UserProductivity toProductivity(
            Personnel p, double hours, double expected) {
        double pct = expected > 0 ? (hours / expected) * 100.0 : 0.0;
        return DashboardProductivityResponse.UserProductivity.builder()
                .personnelId(p.getId())
                .name(p.getName())
                .department(p.getDepartment() != null ? p.getDepartment().name() : null)
                .totalHoursWorked(hours)
                .expectedHours(expected)
                .workingDays(p.getWorkingDaysCount() == null ? 0 : p.getWorkingDaysCount())
                .productivityPercentage(pct)
                .build();
    }

    private List<DashboardResponse.TransactionSummary> buildTransactionSummaries(
            UUID farmId, boolean canSeeInvestments) {

        if (farmId == null) return List.of();

        List<Transaction> transactions = txRepo.findByFarmIdLive(
                        farmId,
                        PageRequest.of(0, DASHBOARD_TX_LIMIT,
                                Sort.by(Sort.Direction.DESC, "occurredAt")))
                .getContent();

        if (transactions.isEmpty()) return List.of();

        List<UUID> txIds = transactions.stream().map(Transaction::getId).toList();

        Map<UUID, List<Income>> incomesByTx = incomeRepo.findByTransactionIdInLive(txIds)
                .stream().collect(Collectors.groupingBy(Income::getTransactionId));

        Map<UUID, List<Expense>> expensesByTx = expenseRepo.findByTransactionIdInLive(txIds)
                .stream().collect(Collectors.groupingBy(Expense::getTransactionId));

        Map<UUID, List<Investment>> investmentsByTx = canSeeInvestments
                ? investmentRepo.findByTransactionIdInLive(txIds)
                  .stream().collect(Collectors.groupingBy(Investment::getTransactionId))
                : Map.of();

        Set<UUID> creatorIds = new HashSet<>();
        incomesByTx.values().forEach(list -> list.forEach(i -> {
            if (i.getCreatedByUserId() != null) creatorIds.add(i.getCreatedByUserId());
        }));
        expensesByTx.values().forEach(list -> list.forEach(e -> {
            if (e.getCreatedByUserId() != null) creatorIds.add(e.getCreatedByUserId());
        }));
        investmentsByTx.values().forEach(list -> list.forEach(v -> {
            if (v.getCreatedByUserId() != null) creatorIds.add(v.getCreatedByUserId());
        }));
        transactions.forEach(tx -> {
            if (tx.getCreatedByUserId() != null) creatorIds.add(tx.getCreatedByUserId());
        });

        Map<UUID, User> userById = creatorIds.isEmpty()
                ? Map.of()
                : authRepository.findAllById(creatorIds).stream()
                  .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<DashboardResponse.TransactionSummary> out = new ArrayList<>(transactions.size());
        for (Transaction tx : transactions) {
            List<Income> incomes = incomesByTx.getOrDefault(tx.getId(), List.of());
            List<Expense> expenses = expensesByTx.getOrDefault(tx.getId(), List.of());
            List<Investment> investments = investmentsByTx.getOrDefault(tx.getId(), List.of());

            BigDecimal totalIncome = incomes.stream()
                    .map(Income::getAmountReceived)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalExpense = expenses.stream()
                    .map(Expense::getAmountPaid)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalInvestment = canSeeInvestments
                    ? investments.stream()
                      .map(Investment::getAmount)
                      .filter(Objects::nonNull)
                      .reduce(BigDecimal.ZERO, BigDecimal::add)
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
                            .createdByUsername(usernameOrNull(i.getCreatedByUserId(), userById))
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
                            .createdByUsername(usernameOrNull(e.getCreatedByUserId(), userById))
                            .build())
                    .toList();

            List<DashboardResponse.InvestmentLine> investmentLines = canSeeInvestments
                    ? investments.stream()
                      .map(v -> DashboardResponse.InvestmentLine.builder()
                                .id(v.getId())
                                .amount(v.getAmount())
                                .currency(v.getCurrency().name())
                                .investorName(v.getInvestorName())
                                .type(v.getType())
                                .description(v.getDescription())
                                .investmentDate(v.getInvestmentDate())
                                .createdByUserId(v.getCreatedByUserId())
                                .createdByUsername(usernameOrNull(v.getCreatedByUserId(), userById))
                                .build())
                      .toList()
                    : null;

            User txCreator = tx.getCreatedByUserId() == null
                    ? null : userById.get(tx.getCreatedByUserId());

            out.add(DashboardResponse.TransactionSummary.builder()
                    .id(tx.getId())
                    .transactionRef(tx.getTransactionRef())
                    .occurredAt(tx.getOccurredAt())
                    .status(tx.getStatus().name())
                    .notes(tx.getNotes())
                    .recordedByUserId(tx.getCreatedByUserId())
                    .recordedByUsername(usernameOrNull(tx.getCreatedByUserId(), userById))
                    .recordedByRole(txCreator == null ? null : txCreator.getRole())
                    .income(totalIncome)
                    .expense(totalExpense)
                    .investment(totalInvestment)
                    .netPosition(netPosition)
                    .incomeCount(incomes.size())
                    .expenseCount(expenses.size())
                    .investmentCount(canSeeInvestments ? investments.size() : null)
                    .incomeLines(incomeLines.isEmpty() ? null : incomeLines)
                    .expenseLines(expenseLines.isEmpty() ? null : expenseLines)
                    .investmentLines(investmentLines == null || investmentLines.isEmpty()
                            ? null : investmentLines)
                    .build());
        }
        return out;
    }

    private DashboardResponse.Totals toTotals(DashboardQueryRepository.Totals src,
                                              boolean canSeeInvestments) {
        if (src == null) return null;

        boolean hasMixedCurrencyTotals = src.totalIncome() == null
                || src.totalExpense() == null
                || src.totalInvestment() == null;

        BigDecimal netPosition = canSeeInvestments && !hasMixedCurrencyTotals
                ? src.totalIncome().subtract(src.totalExpense()).subtract(src.totalInvestment())
                : null;

        BigDecimal netGain = null;
        BigDecimal netLoss = null;
        if (canSeeInvestments && netPosition != null) {
            netGain = netPosition.signum() > 0 ? netPosition : BigDecimal.ZERO;
            netLoss = netPosition.signum() < 0 ? netPosition.abs() : BigDecimal.ZERO;
        }

        return DashboardResponse.Totals.builder()
                .totalTransactions(canSeeInvestments
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

    private String usernameOrNull(UUID userId, Map<UUID, User> userById) {
        if (userId == null) return null;
        User u = userById.get(userId);
        return u != null ? u.getUsername() : "deleted-user";
    }

    private boolean isMasterOrAdmin(String role) {
        return RoleConstants.MASTER.equals(role) || RoleConstants.ADMIN.equals(role);
    }
}