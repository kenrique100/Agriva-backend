package com.agriva.finance.repository;

import com.agriva.finance.model.entity.StagedExpense;
import com.agriva.finance.model.entity.StagedExpenseId;
import com.agriva.finance.model.enums.StagedExpenseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StagedExpenseRepository extends JpaRepository<StagedExpense, StagedExpenseId> {

    Page<StagedExpense> findByFarmId(UUID farmId, Pageable pageable);

    Page<StagedExpense> findByFarmIdAndStatus(UUID farmId,
                                              StagedExpenseStatus status,
                                              Pageable pageable);

    long countByFarmIdAndStatus(UUID farmId, StagedExpenseStatus status);

    @Query("""
            SELECT s FROM StagedExpense s
            WHERE s.status = :pending
               OR (s.status = :retrying
                   AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= :now))
            ORDER BY s.submittedAt ASC
            """)
    List<StagedExpense> findDueForProcessing(
            @Param("now") LocalDateTime now,
            @Param("pending") StagedExpenseStatus pending,
            @Param("retrying") StagedExpenseStatus retrying,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StagedExpense s WHERE s.id = :id")
    Optional<StagedExpense> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT s FROM StagedExpense s WHERE s.id = :id ORDER BY s.submittedAt DESC")
    List<StagedExpense> findAllByEventId(@Param("id") UUID id);

    default Optional<StagedExpense> findByIdOnly(UUID id) {
        return findAllByEventId(id).stream().findFirst();
    }

    @Modifying
    @Query("DELETE FROM StagedExpense s WHERE s.farmId = :farmId")
    int deleteByFarmId(@Param("farmId") UUID farmId);
}