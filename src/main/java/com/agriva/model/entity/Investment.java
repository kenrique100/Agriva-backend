package com.agriva.model.entity;

import com.agriva.model.entity.enums.Currency;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "investment")
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Investment extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    @Builder.Default
    private Currency currency = Currency.USD;

    @Column(name = "investor_name", nullable = false, length = 100)
    private String investorName;

    @Column(length = 50)
    private String type;

    @Column(length = 500)
    private String description;

    @Column(name = "investment_date", nullable = false)
    private LocalDate investmentDate;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = false;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Version
    @Column(nullable = false)
    private Long version;
}