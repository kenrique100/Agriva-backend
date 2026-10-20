package com.agriva.repository;

import com.agriva.model.entity.Income;
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
public interface IncomeRepository extends JpaRepository<Income, UUID> {

    Page<Income> findByFarmId(UUID farmId, Pageable pageable);

    Optional<Income> findByIdAndFarmId(UUID id, UUID farmId);

    boolean existsByFarmIdAndAmountReceivedAndIncomeDateAndReason(
            UUID farmId, BigDecimal amountReceived, LocalDate incomeDate, String reason);

    List<Income> findByTransactionId(UUID transactionId);

    List<Income> findByFarmIdAndTransactionIdIsNull(UUID farmId);

    long countByFarmId(UUID farmId);

    long countByFarmIdAndCreatedByUserId(UUID farmId, UUID userId);

    @Query("SELECT SUM(i.amountReceived) FROM Income i WHERE i.farmId = :farmId")
    BigDecimal sumAmountByFarmId(@Param("farmId") UUID farmId);

    @Query("SELECT SUM(i.amountReceived) FROM Income i WHERE i.farmId = :farmId AND i.createdByUserId = :userId")
    BigDecimal sumAmountByFarmIdAndCreatedByUserId(@Param("farmId") UUID farmId,
                                                   @Param("userId") UUID userId);

    @Query("SELECT i.createdByUserId, SUM(i.amountReceived) FROM Income i " +
            "WHERE i.farmId = :farmId GROUP BY i.createdByUserId")
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