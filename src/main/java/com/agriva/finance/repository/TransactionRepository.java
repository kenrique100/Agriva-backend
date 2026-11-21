package com.agriva.finance.repository;

import com.agriva.finance.model.entity.Transaction;
import com.agriva.finance.model.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @Query("SELECT t FROM Transaction t WHERE t.farmId = :farmId AND t.deletedAt IS NULL")
    Page<Transaction> findByFarmIdLive(@Param("farmId") UUID farmId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.id = :id AND t.farmId = :farmId AND t.deletedAt IS NULL")
    Optional<Transaction> findByIdAndFarmIdLive(@Param("id") UUID id, @Param("farmId") UUID farmId);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Transaction t " +
            "WHERE t.farmId = :farmId AND t.transactionRef = :ref AND t.deletedAt IS NULL")
    boolean existsByFarmIdAndTransactionRefLive(@Param("farmId") UUID farmId, @Param("ref") String transactionRef);

    long countByFarmId(UUID farmId);

    long countByFarmIdAndStatus(UUID farmId, TransactionStatus status);

    @Modifying
    @Query("UPDATE Transaction t SET t.deletedAt = :now WHERE t.farmId = :farmId AND t.deletedAt IS NULL")
    int softDeleteByFarmId(@Param("farmId") UUID farmId, @Param("now") LocalDateTime now);
}