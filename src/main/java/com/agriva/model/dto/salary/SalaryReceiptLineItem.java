package com.agriva.model.dto.salary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryReceiptLineItem {
    private String title;
    private BigDecimal expectedHours;
    private BigDecimal actualHours;
    private BigDecimal completionRate;
    private String status;
}