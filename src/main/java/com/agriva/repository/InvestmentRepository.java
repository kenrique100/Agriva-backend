package com.agriva.repository;

import com.agriva.model.entity.Investment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvestmentRepository extends JpaRepository<Investment, UUID> {

    Page<Investment> findByFarmId(UUID farmId, Pageable pageable);

    Optional<Investment> findByIdAndFarmId(UUID id, UUID farmId);

    List<Investment> findByTransactionId(UUID transactionId);

    List<Investment> findByTransactionIdIn(Collection<UUID> transactionIds);

    long countByFarmId(UUID farmId);

    long countByFarmIdAndCreatedByUserId(UUID farmId, UUID userId);

    @Query("SELECT SUM(i.amount) FROM Investment i WHERE i.farmId = :farmId")
    BigDecimal sumAmountByFarmId(@Param("farmId") UUID farmId);

    @Query("SELECT SUM(i.amount) FROM Investment i WHERE i.farmId = :farmId AND i.createdByUserId = :userId")
    BigDecimal sumAmountByFarmIdAndCreatedByUserId(@Param("farmId") UUID farmId,
                                                   @Param("userId") UUID userId);

    @Query("SELECT i.currency, SUM(i.amount) FROM Investment i " +
            "WHERE i.farmId = :farmId GROUP BY i.currency")
    List<Object[]> sumAmountGroupedByCurrency(@Param("farmId") UUID farmId);

    @Query("SELECT i.currency, SUM(i.amount) FROM Investment i " +
            "WHERE i.farmId = :farmId AND i.createdByUserId = :userId GROUP BY i.currency")
    List<Object[]> sumAmountGroupedByCurrencyAndCreatedByUserId(@Param("farmId") UUID farmId,
                                                                @Param("userId") UUID userId);

    default BigDecimal sumAmountByFarmIdOrZero(UUID farmId) {
        BigDecimal v = sumAmountByFarmId(farmId);
        return v == null ? BigDecimal.ZERO : v;
    }

    default BigDecimal sumAmountByFarmIdAndUserOrZero(UUID farmId, UUID userId) {
        BigDecimal v = sumAmountByFarmIdAndCreatedByUserId(farmId, userId);
        return v == null ? BigDecimal.ZERO : v;
    }

    Optional<Investment> findFirstByFarmIdAndActiveTrue(UUID farmId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Investment i WHERE i.farmId = :farmId AND i.active = true")
    Optional<Investment> findActiveForUpdate(@Param("farmId") UUID farmId);

    @Query("SELECT SUM(e.amountPaid) FROM Expense e WHERE e.investmentId = :investmentId")
    BigDecimal sumExpensesByInvestmentId(@Param("investmentId") UUID investmentId);

    @Query("SELECT SUM(e.amountPaid) FROM Expense e " +
            "WHERE e.investmentId = :investmentId AND e.id <> :excludeExpenseId")
    BigDecimal sumExpensesByInvestmentIdExcluding(@Param("investmentId") UUID investmentId,
                                                  @Param("excludeExpenseId") UUID excludeExpenseId);

    default BigDecimal sumExpensesByInvestmentIdOrZero(UUID investmentId) {
        BigDecimal v = sumExpensesByInvestmentId(investmentId);
        return v == null ? BigDecimal.ZERO : v;
    }

    default BigDecimal sumExpensesByInvestmentIdExcludingOrZero(UUID investmentId, UUID excludeExpenseId) {
        BigDecimal v = sumExpensesByInvestmentIdExcluding(investmentId, excludeExpenseId);
        return v == null ? BigDecimal.ZERO : v;
    }
}