package com.agriva.finance.repository;

import com.agriva.finance.model.entity.Investment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvestmentRepository extends JpaRepository<Investment, UUID> {

    // Live reads
    @Query("SELECT i FROM Investment i WHERE i.farmId = :farmId AND i.deletedAt IS NULL")
    Page<Investment> findByFarmIdLive(@Param("farmId") UUID farmId, Pageable p);

    @Query("SELECT i FROM Investment i WHERE i.id = :id AND i.farmId = :farmId " +
            "AND i.deletedAt IS NULL")
    Optional<Investment> findByIdAndFarmIdLive(@Param("id") UUID id,
                                               @Param("farmId") UUID farmId);

    @Query("SELECT i FROM Investment i WHERE i.transactionId = :txId AND i.deletedAt IS NULL")
    List<Investment> findByTransactionIdLive(@Param("txId") UUID txId);

    @Query("SELECT i FROM Investment i WHERE i.transactionId IN :ids AND i.deletedAt IS NULL")
    List<Investment> findByTransactionIdInLive(@Param("ids") Collection<UUID> ids);

    // Counts
    long countByFarmId(UUID farmId);
    long countByFarmIdAndCreatedByUserId(UUID farmId, UUID userId);

    // Aggregates
    @Query("SELECT SUM(i.amount) FROM Investment i WHERE i.farmId = :farmId AND i.deletedAt IS NULL")
    BigDecimal sumAmountByFarmId(@Param("farmId") UUID farmId);

    @Query("SELECT i.currency, SUM(i.amount) FROM Investment i " +
            "WHERE i.farmId = :farmId AND i.deletedAt IS NULL GROUP BY i.currency")
    List<Object[]> sumAmountGroupedByCurrency(@Param("farmId") UUID farmId);

    @Query("SELECT i.currency, SUM(i.amount) FROM Investment i " +
            "WHERE i.farmId = :farmId AND i.createdByUserId = :userId AND i.deletedAt IS NULL " +
            "GROUP BY i.currency")
    List<Object[]> sumAmountGroupedByCurrencyAndCreatedByUserId(
            @Param("farmId") UUID farmId, @Param("userId") UUID userId);

    @Query("SELECT i.createdByUserId, COUNT(i) FROM Investment i " +
            "WHERE i.farmId = :farmId AND i.deletedAt IS NULL AND i.createdByUserId IS NOT NULL " +
            "GROUP BY i.createdByUserId")
    List<Object[]> countByFarmIdGroupedByCreator(@Param("farmId") UUID farmId);

    @Query("SELECT i.createdByUserId, SUM(i.amount) FROM Investment i " +
            "WHERE i.farmId = :farmId AND i.deletedAt IS NULL AND i.createdByUserId IS NOT NULL " +
            "GROUP BY i.createdByUserId")
    List<Object[]> sumGroupedByCreator(@Param("farmId") UUID farmId);

    @Query("SELECT e.investmentId, SUM(e.amountPaid) FROM Expense e " +
            "WHERE e.investmentId IN :ids AND e.deletedAt IS NULL GROUP BY e.investmentId")
    List<Object[]> sumExpensesByInvestmentIdIn(@Param("ids") Collection<UUID> ids);

    default BigDecimal sumAmountByFarmIdOrZero(UUID farmId) {
        BigDecimal v = sumAmountByFarmId(farmId);
        return v == null ? BigDecimal.ZERO : v;
    }

    // Active investment lookups
    @Query("SELECT i FROM Investment i WHERE i.farmId = :farmId " +
            "AND i.active = true AND i.deletedAt IS NULL")
    Optional<Investment> findFirstByFarmIdAndActiveTrue(@Param("farmId") UUID farmId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Investment i WHERE i.farmId = :farmId " +
            "AND i.active = true AND i.deletedAt IS NULL")
    Optional<Investment> findActiveForUpdate(@Param("farmId") UUID farmId);

    // ---- Expense-sum lookups for one investment ----
    @Query("SELECT SUM(e.amountPaid) FROM Expense e " +
            "WHERE e.investmentId = :investmentId AND e.deletedAt IS NULL")
    BigDecimal sumExpensesByInvestmentId(@Param("investmentId") UUID investmentId);

    @Query("SELECT SUM(e.amountPaid) FROM Expense e " +
            "WHERE e.investmentId = :investmentId AND e.id <> :excludeExpenseId " +
            "AND e.deletedAt IS NULL")
    BigDecimal sumExpensesByInvestmentIdExcluding(@Param("investmentId") UUID investmentId,
                                                  @Param("excludeExpenseId") UUID excludeExpenseId);

    default BigDecimal sumExpensesByInvestmentIdOrZero(UUID investmentId) {
        BigDecimal v = sumExpensesByInvestmentId(investmentId);
        return v == null ? BigDecimal.ZERO : v;
    }

    default BigDecimal sumExpensesByInvestmentIdExcludingOrZero(UUID investmentId,
                                                                UUID excludeExpenseId) {
        BigDecimal v = sumExpensesByInvestmentIdExcluding(investmentId, excludeExpenseId);
        return v == null ? BigDecimal.ZERO : v;
    }

    @Modifying
    @Query("UPDATE Investment i SET i.deletedAt = :now " +
            "WHERE i.farmId = :farmId AND i.deletedAt IS NULL")
    int softDeleteByFarmId(@Param("farmId") UUID farmId,
                           @Param("now") LocalDateTime now);
}