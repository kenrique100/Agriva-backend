package com.agriva.model.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardResponse {

    private UUID farmId;
    private String farmName;

    private UUID userId;
    private String username;
    private String role;

    private Totals personal;
    private Totals farm;

    private List<UserActivity> users;
    private List<TransactionSummary> transactions;

    // ---------------- Totals ----------------

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Totals {
        private Long totalTransactions;
        private Long totalIncomes;
        private Long totalExpenses;
        private Long totalInvestments;

        private BigDecimal totalIncome;
        private BigDecimal totalExpense;
        private BigDecimal totalInvestment;

        private BigDecimal netGain;
        private BigDecimal netLoss;
        private BigDecimal netPosition;
    }

    // ---------------- Per-user activity ----------------

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserActivity {
        private UUID userId;
        private String email;
        private String username;
        private String role;
        private Totals activitySummary;
    }

    // ---------------- Transactions with line items ----------------

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TransactionSummary {
        private UUID id;
        private String transactionRef;
        private LocalDateTime occurredAt;
        private String status;
        private String notes;

        private UUID recordedByUserId;
        private String recordedByUsername;
        private String recordedByRole;

        // aggregate amounts
        private BigDecimal income;
        private BigDecimal expense;
        private BigDecimal investment;
        private BigDecimal netPosition;

        private Integer incomeCount;
        private Integer expenseCount;
        private Integer investmentCount;

        // individual rows, added
        private List<IncomeLine> incomeLines;
        private List<ExpenseLine> expenseLines;
        private List<InvestmentLine> investmentLines;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IncomeLine {
        private UUID id;
        private BigDecimal amount;
        private String currency;
        private String source;
        private String category;
        private String description;
        private LocalDate incomeDate;
        private UUID createdByUserId;
        private String createdByUsername;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ExpenseLine {
        private UUID id;
        private BigDecimal amount;
        private String currency;
        private String category;
        private String vendor;
        private String description;
        private LocalDate expenseDate;
        private UUID createdByUserId;
        private String createdByUsername;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class InvestmentLine {
        private UUID id;
        private BigDecimal amount;
        private String currency;
        private String investorName;
        private String type;
        private String description;
        private LocalDate investmentDate;
        private UUID createdByUserId;
        private String createdByUsername;
    }
}