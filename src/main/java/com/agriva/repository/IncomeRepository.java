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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncomeRepository extends JpaRepository<Income, UUID> {

    // ---- Live (soft-delete aware) reads ----
    @Query("SELECT i FROM Income i WHERE i.farmId = :farmId AND i.deletedAt IS NULL")
    Page<Income> findByFarmIdLive(@Param("farmId") UUID farmId, Pageable pageable);

    @Query("SELECT i FROM Income i WHERE i.id = :id AND i.farmId = :farmId AND i.deletedAt IS NULL")
    Optional<Income> findByIdAndFarmIdLive(@Param("id") UUID id, @Param("farmId") UUID farmId);

    @Query("SELECT i FROM Income i WHERE i.transactionId = :txId AND i.deletedAt IS NULL")
    List<Income> findByTransactionIdLive(@Param("txId") UUID txId);

    @Query("SELECT i FROM Income i WHERE i.transactionId IN :ids AND i.deletedAt IS NULL")
    List<Income> findByTransactionIdInLive(@Param("ids") Collection<UUID> ids);

    @Query("SELECT i FROM Income i WHERE i.farmId = :farmId " +
            "AND i.transactionId IS NULL AND i.deletedAt IS NULL")
    List<Income> findByFarmIdAndTransactionIdIsNull(@Param("farmId") UUID farmId);

    // ---- Duplicate / file-ownership checks ----
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Income i " +
            "WHERE i.farmId = :farmId AND i.amountReceived = :amountReceived " +
            "AND i.incomeDate = :incomeDate AND i.reason = :reason AND i.deletedAt IS NULL")
    boolean existsByFarmIdAndAmountReceivedAndIncomeDateAndReasonLive(
            @Param("farmId") UUID farmId,
            @Param("amountReceived") BigDecimal amountReceived,
            @Param("incomeDate") LocalDate incomeDate,
            @Param("reason") String reason);

    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Income i " +
            "WHERE i.farmId = :farmId AND i.receipt = :receipt AND i.deletedAt IS NULL")
    boolean existsByFarmIdAndReceipt(@Param("farmId") UUID farmId,
                                     @Param("receipt") String receipt);

    // ---- Counts ----
    long countByFarmId(UUID farmId);
    long countByFarmIdAndCreatedByUserId(UUID farmId, UUID userId);

    // ---- Aggregates ----
    @Query("SELECT SUM(i.amountReceived) FROM Income i WHERE i.farmId = :farmId AND i.deletedAt IS NULL")
    BigDecimal sumAmountByFarmId(@Param("farmId") UUID farmId);

    @Query("SELECT SUM(i.amountReceived) FROM Income i " +
            "WHERE i.farmId = :farmId AND i.createdByUserId = :userId AND i.deletedAt IS NULL")
    BigDecimal sumAmountByFarmIdAndCreatedByUserId(@Param("farmId") UUID farmId,
                                                   @Param("userId") UUID userId);

    @Query("SELECT i.currency, SUM(i.amountReceived) FROM Income i " +
            "WHERE i.farmId = :farmId AND i.deletedAt IS NULL GROUP BY i.currency")
    List<Object[]> sumAmountGroupedByCurrency(@Param("farmId") UUID farmId);

    /**
     * Per-user currency grouping — required by DashboardQueryRepositoryImpl.totalsForUser.
     * Preserves the mixed-currency detection behaviour.
     */
    @Query("SELECT i.currency, SUM(i.amountReceived) FROM Income i " +
            "WHERE i.farmId = :farmId AND i.createdByUserId = :userId AND i.deletedAt IS NULL " +
            "GROUP BY i.currency")
    List<Object[]> sumAmountGroupedByCurrencyAndCreatedByUserId(
            @Param("farmId") UUID farmId, @Param("userId") UUID userId);

    // ---- Per-creator batch aggregates (dashboard/farm details) ----
    @Query("SELECT i.createdByUserId, COUNT(i) FROM Income i " +
            "WHERE i.farmId = :farmId AND i.deletedAt IS NULL AND i.createdByUserId IS NOT NULL " +
            "GROUP BY i.createdByUserId")
    List<Object[]> countByFarmIdGroupedByCreator(@Param("farmId") UUID farmId);

    @Query("SELECT i.createdByUserId, SUM(i.amountReceived) FROM Income i " +
            "WHERE i.farmId = :farmId AND i.deletedAt IS NULL AND i.createdByUserId IS NOT NULL " +
            "GROUP BY i.createdByUserId")
    List<Object[]> sumGroupedByCreator(@Param("farmId") UUID farmId);

    default BigDecimal sumAmountByFarmIdOrZero(UUID farmId) {
        BigDecimal v = sumAmountByFarmId(farmId);
        return v == null ? BigDecimal.ZERO : v;
    }
}