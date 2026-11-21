package com.agriva.finance.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseStatsResponse {
    private BigDecimal totalPaid;
    private BigDecimal totalExpected;
    private BigDecimal totalDue;
    private long totalRecords;
}