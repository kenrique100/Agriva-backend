package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.StagedFeedingProduction;
import com.agriva.feeding.model.entity.StagedFeedingProductionId;
import com.agriva.shared.models.enums.StagedStatus;
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
public interface StagedFeedingProductionRepository
        extends JpaRepository<StagedFeedingProduction, StagedFeedingProductionId> {

    Page<StagedFeedingProduction> findByFarmId(UUID farmId, Pageable pageable);
    Page<StagedFeedingProduction> findByFarmIdAndStatus(UUID farmId, StagedStatus status, Pageable pageable);
    long countByFarmIdAndStatus(UUID farmId, StagedStatus status);

    List<StagedFeedingProduction> findByFarmIdAndBatchIdOrderByRowNumberAsc(UUID farmId, UUID batchId);

    @Query("""
            SELECT s FROM StagedFeedingProduction s
            WHERE s.status = :pending
            OR (s.status = :retrying AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= :now))
            ORDER BY s.submittedAt ASC
            """)
    List<StagedFeedingProduction> findDueForProcessing(
            @Param("now") LocalDateTime now,
            @Param("pending") StagedStatus pending,
            @Param("retrying") StagedStatus retrying,
            Pageable pageable);

    @Query("""
            SELECT s FROM StagedFeedingProduction s
            WHERE s.farmId = :farmId
              AND (s.status = :pending
                   OR (s.status = :retrying
                       AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= :now)))
            ORDER BY s.submittedAt ASC
            """)
    List<StagedFeedingProduction> findDueForProcessing(
            @Param("farmId") UUID farmId,
            @Param("now") LocalDateTime now,
            @Param("pending") StagedStatus pending,
            @Param("retrying") StagedStatus retrying,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT s.farmId FROM StagedFeedingProduction s
            WHERE s.status = :pending
            OR (s.status = :retrying AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= :now))
            """)
    List<UUID> findDistinctFarmIdsWithDueRows(
            @Param("now") LocalDateTime now,
            @Param("pending") StagedStatus pending,
            @Param("retrying") StagedStatus retrying);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StagedFeedingProduction s WHERE s.id = :id")
    Optional<StagedFeedingProduction> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM StagedFeedingProduction s WHERE s.farmId = :farmId")
    int deleteByFarmId(@Param("farmId") UUID farmId);
}