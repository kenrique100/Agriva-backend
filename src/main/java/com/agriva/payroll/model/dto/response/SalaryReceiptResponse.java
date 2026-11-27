package com.agriva.payroll.model.dto.response;

import com.agriva.shared.models.enums.Currency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryReceiptResponse {
    private String receiptNumber;
    private LocalDate issueDate;
    private String farmName;
    private String managerName;

    // Personnel block
    private String personnelName;
    private String personnelNationalId;
    private String department;
    private LocalDate employmentDate;

    // Salary block
    private BigDecimal amount;
    private Currency currency;
    private LocalDate paymentDate;
    private String paymentReference;
    private String status;

    // Productivity block
    private List<SalaryReceiptLineItem> tasks;
    private BigDecimal totalExpectedHours;
    private BigDecimal totalActualHours;
    private BigDecimal overallProductivity;
    private Integer workingDays;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
}