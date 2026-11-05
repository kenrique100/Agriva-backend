package com.agriva.payroll.model.dto.response;

import com.agriva.payroll.model.enums.SalaryPaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalaryPaymentResponse {
    private UUID id;
    private UUID farmId;
    private UUID personnelId;
    private String personnelName;
    private BigDecimal amount;
    private LocalDate paymentDate;
    private SalaryPaymentStatus status;
    private String paymentReference;
    private UUID expenseId;
    private UUID paidByUserId;
    private LocalDateTime createdAt;
}