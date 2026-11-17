package com.agriva.farming.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FarmDetailsResponse {

    private UUID farmId;
    private String farmName;
    private UUID masterId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer totalUsers;
    private Integer totalMasters;
    private Integer totalSubUsers;
    private Long totalIncomes;
    private BigDecimal totalIncomeAmount;
    private Long totalExpenses;
    private BigDecimal totalExpenseAmount;
    private Long totalInvestments;
    private BigDecimal totalInvestmentAmount;
    private Long totalTransactions;
    private BigDecimal netGain;
    private BigDecimal netLoss;
    private BigDecimal netPosition;
    private List<FarmUser> users;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FarmUser {
        private UUID userId;
        private String email;
        private String username;
        private String role;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private UserActivity activitySummary;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UserActivity {
        private Long totalIncomes;
        private Long totalExpenses;
        private Long totalInvestments;
        private BigDecimal totalIncome;
        private BigDecimal totalExpense;
        private BigDecimal totalInvestment;
        private BigDecimal netPosition;
    }
}