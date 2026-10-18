package com.agriva.model.dto.farm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FarmListResponse {

    private int totalFarms;
    private int totalUsers;
    private int totalMasters;
    private int totalSubUsers;

    private long totalTransactions;
    private BigDecimal grandTotalIncome;
    private BigDecimal grandTotalExpense;
    private BigDecimal grandTotalInvestment;
    private BigDecimal grandNetPosition;

    private List<FarmDetailsResponse> farms;
}