package com.agriva.repository;

import com.agriva.model.entity.StagedExpense;
import com.agriva.model.entity.enums.StagedExpenseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface StagedExpenseRepository extends JpaRepository<StagedExpense, UUID> {

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
}