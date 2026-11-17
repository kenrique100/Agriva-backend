package com.agriva.finance.repository;

import com.agriva.finance.model.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    // OFFSET pagination
    @Query("SELECT e FROM Expense e WHERE e.farmId = :farmId AND e.deletedAt IS NULL")
    Page<Expense> findByFarmIdLive(@Param("farmId") UUID farmId, Pageable pageable);

    // Keyset pagination
    @Query("""
        SELECT e FROM Expense e
        WHERE e.farmId = :farmId
          AND e.deletedAt IS NULL
          AND (:cursorDate IS NULL
               OR e.expenseDate < :cursorDate
               OR (e.expenseDate = :cursorDate AND e.id < :cursorId))
        ORDER BY e.expenseDate DESC, e.id DESC
    """)
    List<Expense> findCursorPage(
            @Param("farmId") UUID farmId,
            @Param("cursorDate") LocalDate cursorDate,
            @Param("cursorId") UUID cursorId,
            Pageable limit);

    // Single-row reads
    @Query("SELECT e FROM Expense e WHERE e.id = :id AND e.farmId = :farmId AND e.deletedAt IS NULL")
    Optional<Expense> findByIdAndFarmIdLive(@Param("id") UUID id, @Param("farmId") UUID farmId);

    @Query("SELECT e FROM Expense e WHERE e.transactionId = :txId AND e.deletedAt IS NULL")
    List<Expense> findByTransactionIdLive(@Param("txId") UUID txId);

    @Query("SELECT e FROM Expense e WHERE e.transactionId IN :ids AND e.deletedAt IS NULL")
    List<Expense> findByTransactionIdInLive(@Param("ids") Collection<UUID> ids);

    // Duplicate / receipt-ownership checks
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN TRUE ELSE FALSE END FROM Expense e " +
            "WHERE e.farmId = :farmId AND e.amountPaid = :amountPaid " +
            "AND e.expenseDate = :expenseDate AND e.reason = :reason AND e.deletedAt IS NULL")
    boolean existsByFarmIdAndAmountPaidAndExpenseDateAndReasonLive(
            @Param("farmId") UUID farmId,
            @Param("amountPaid") BigDecimal amountPaid,
            @Param("expenseDate") LocalDate expenseDate,
            @Param("reason") String reason);

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN TRUE ELSE FALSE END FROM Expense e " +
            "WHERE e.farmId = :farmId AND e.receipt = :receipt AND e.deletedAt IS NULL")
    boolean existsByFarmIdAndReceipt(@Param("farmId") UUID farmId,
                                     @Param("receipt") String receipt);

    // Counts / aggregates
    long countByFarmId(UUID farmId);
    long countByFarmIdAndCreatedByUserId(UUID farmId, UUID userId);

    @Query("SELECT SUM(e.amountPaid) FROM Expense e WHERE e.farmId = :farmId AND e.deletedAt IS NULL")
    BigDecimal sumAmountByFarmId(@Param("farmId") UUID farmId);

    @Query("SELECT SUM(e.amountPaid) FROM Expense e " +
            "WHERE e.farmId = :farmId AND e.createdByUserId = :userId AND e.deletedAt IS NULL")
    BigDecimal sumAmountByFarmIdAndCreatedByUserId(@Param("farmId") UUID farmId,
                                                   @Param("userId") UUID userId);

    @Query("SELECT e.currency, SUM(e.amountPaid) FROM Expense e " +
            "WHERE e.farmId = :farmId AND e.deletedAt IS NULL GROUP BY e.currency")
    List<Object[]> sumAmountGroupedByCurrency(@Param("farmId") UUID farmId);

    @Query("SELECT e.currency, SUM(e.amountPaid) FROM Expense e " +
            "WHERE e.farmId = :farmId AND e.createdByUserId = :userId AND e.deletedAt IS NULL " +
            "GROUP BY e.currency")
    List<Object[]> sumAmountGroupedByCurrencyAndCreatedByUserId(
            @Param("farmId") UUID farmId, @Param("userId") UUID userId);

    @Query("SELECT e.createdByUserId, COUNT(e) FROM Expense e " +
            "WHERE e.farmId = :farmId AND e.deletedAt IS NULL AND e.createdByUserId IS NOT NULL " +
            "GROUP BY e.createdByUserId")
    List<Object[]> countByFarmIdGroupedByCreator(@Param("farmId") UUID farmId);

    @Query("SELECT e.createdByUserId, SUM(e.amountPaid) FROM Expense e " +
            "WHERE e.farmId = :farmId AND e.deletedAt IS NULL AND e.createdByUserId IS NOT NULL " +
            "GROUP BY e.createdByUserId")
    List<Object[]> sumGroupedByCreator(@Param("farmId") UUID farmId);

    default BigDecimal sumAmountByFarmIdOrZero(UUID farmId) {
        BigDecimal v = sumAmountByFarmId(farmId);
        return v == null ? BigDecimal.ZERO : v;
    }

    @Query("SELECT e.receipt FROM Expense e " +
            "WHERE e.farmId = :farmId AND e.receipt IS NOT NULL")
    List<String> findReceiptsByFarmId(@Param("farmId") UUID farmId);

    @Modifying
    @Query("UPDATE Expense e SET e.deletedAt = :now " +
            "WHERE e.farmId = :farmId AND e.deletedAt IS NULL")
    int softDeleteByFarmId(@Param("farmId") UUID farmId,
                           @Param("now") LocalDateTime now);
}