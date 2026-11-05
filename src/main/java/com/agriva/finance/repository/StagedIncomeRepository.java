package com.agriva.finance.repository;

import com.agriva.finance.model.entity.StagedIncome;
import com.agriva.finance.model.enums.StagedIncomeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StagedIncomeRepository extends JpaRepository<StagedIncome, UUID> {

    Page<StagedIncome> findByFarmId(UUID farmId, Pageable pageable);

    Page<StagedIncome> findByFarmIdAndStatus(UUID farmId,
                                             StagedIncomeStatus status,
                                             Pageable pageable);

    long countByFarmIdAndStatus(UUID farmId, StagedIncomeStatus status);

    @Query("""
            SELECT s FROM StagedIncome s
            WHERE s.status = :pending
               OR (s.status = :retrying
                   AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= :now))
            ORDER BY s.submittedAt ASC
            """)
    List<StagedIncome> findDueForProcessing(
            @Param("now") LocalDateTime now,
            @Param("pending") StagedIncomeStatus pending,
            @Param("retrying") StagedIncomeStatus retrying,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StagedIncome s WHERE s.id = :id")
    Optional<StagedIncome> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM StagedIncome s WHERE s.farmId = :farmId")
    int deleteByFarmId(@Param("farmId") UUID farmId);
}