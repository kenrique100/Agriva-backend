package com.agriva.model.dto.salary;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SalaryReceiptResponse {

    private String receiptNumber;
    private LocalDate issueDate;

    private String personnelName;
    private String personnelNationalId;
    private String department;
    private LocalDate employmentDate;

    private BigDecimal amount;
    private LocalDate paymentDate;
    private String paymentReference;
    private String status;

    private List<SalaryReceiptLineItem> tasks;
    private BigDecimal totalExpectedHours;
    private BigDecimal totalActualHours;
    private BigDecimal overallProductivity;
    private Integer workingDays;
}