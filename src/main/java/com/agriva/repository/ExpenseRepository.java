package com.agriva.repository;

import com.agriva.model.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    Page<Expense> findByFarmId(UUID farmId, Pageable pageable);

    Optional<Expense> findByIdAndFarmId(UUID id, UUID farmId);

    boolean existsByFarmIdAndAmountPaidAndExpenseDateAndReason(
            UUID farmId, BigDecimal amountPaid, LocalDate expenseDate, String reason);

    List<Expense> findByTransactionId(UUID transactionId);

    long countByFarmId(UUID farmId);

    long countByFarmIdAndCreatedByUserId(UUID farmId, UUID userId);

    @Query("SELECT SUM(e.amountPaid) FROM Expense e WHERE e.farmId = :farmId")
    BigDecimal sumAmountByFarmId(@Param("farmId") UUID farmId);

    @Query("SELECT SUM(e.amountPaid) FROM Expense e WHERE e.farmId = :farmId AND e.createdByUserId = :userId")
    BigDecimal sumAmountByFarmIdAndCreatedByUserId(@Param("farmId") UUID farmId,
                                                   @Param("userId") UUID userId);

    @Query("SELECT e.createdByUserId, SUM(e.amountPaid) FROM Expense e " +
            "WHERE e.farmId = :farmId GROUP BY e.createdByUserId")
    List<Object[]> sumAmountGroupedByUser(@Param("farmId") UUID farmId);

    default BigDecimal sumAmountByFarmIdOrZero(UUID farmId) {
        BigDecimal v = sumAmountByFarmId(farmId);
        return v == null ? BigDecimal.ZERO : v;
    }

    default BigDecimal sumAmountByFarmIdAndUserOrZero(UUID farmId, UUID userId) {
        BigDecimal v = sumAmountByFarmIdAndCreatedByUserId(farmId, userId);
        return v == null ? BigDecimal.ZERO : v;
    }
}