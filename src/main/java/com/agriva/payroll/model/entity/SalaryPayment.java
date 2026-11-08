package com.agriva.payroll.model.entity;

import com.agriva.payroll.model.enums.SalaryPaymentStatus;
import com.agriva.personnel.models.entity.Personnel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "salary_payments",
        uniqueConstraints = @UniqueConstraint(name = "ux_salary_payments_ref",
                columnNames = "payment_reference"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaryPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "personnel_id", nullable = false)
    private UUID personnelId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SalaryPaymentStatus status = SalaryPaymentStatus.PROCESSED;

    @Column(name = "payment_reference", nullable = false, length = 50)
    private String paymentReference;

    /** FK to the Expense row this salary produced — the financial source of truth. */
    @Column(name = "expense_id")
    private UUID expenseId;

    @Column(name = "paid_by_user_id")
    private UUID paidByUserId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}