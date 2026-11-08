package com.agriva.finance.model.entity;

import com.agriva.shared.models.enums.Currency;
import com.agriva.finance.model.enums.ExpenseCategory;
import com.agriva.shared.models.entity.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "expense")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Expense extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "investment_id")
    private UUID investmentId;

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

    @Version
    @Column(nullable = false)
    private Long version;

    public void calculateDueBalance() {
        this.dueBalance = (expectedAmount != null && amountPaid != null)
                ? expectedAmount.subtract(amountPaid)
                : BigDecimal.ZERO;
    }
}