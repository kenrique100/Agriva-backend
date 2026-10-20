package com.agriva.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "expense")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "investment_id")
    private UUID investmentId;                 // NEW — active investment that funds this expense

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(nullable = false)
    @Builder.Default
    private int qtyPurchased = 1;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 4)
    private BigDecimal amountPaid;

    @Column(name = "expected_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedAmount;

    @Column(name = "due_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal dueBalance;

    @Column(length = 255)
    private String receipt;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    @Builder.Default
    private Currency currency = Currency.XAF;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ExpenseCategory category;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void calculateDueBalance() {
        if (this.expectedAmount != null && this.amountPaid != null) {
            this.dueBalance = this.expectedAmount.subtract(this.amountPaid);
        } else {
            this.dueBalance = BigDecimal.ZERO;
        }
    }
}