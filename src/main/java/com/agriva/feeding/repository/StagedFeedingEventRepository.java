package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.StagedFeedingEvent;
import com.agriva.feeding.model.entity.StagedFeedingEventId;
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
public interface StagedFeedingEventRepository
        extends JpaRepository<StagedFeedingEvent, StagedFeedingEventId> {

    Page<StagedFeedingEvent> findByFarmId(UUID farmId, Pageable pageable);
    Page<StagedFeedingEvent> findByFarmIdAndStatus(UUID farmId, StagedStatus status, Pageable pageable);
    long countByFarmIdAndStatus(UUID farmId, StagedStatus status);

    List<StagedFeedingEvent> findByFarmIdAndBatchIdOrderByRowNumberAsc(UUID farmId, UUID batchId);

    @Query("""
            SELECT s FROM StagedFeedingEvent s
            WHERE s.status = :pending
            OR (s.status = :retrying AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= :now))
            ORDER BY s.submittedAt ASC
            """)
    List<StagedFeedingEvent> findDueForProcessing(
            @Param("now") LocalDateTime now,
            @Param("pending") StagedStatus pending,
            @Param("retrying") StagedStatus retrying,
            Pageable pageable);

    @Query("""
            SELECT s FROM StagedFeedingEvent s
            WHERE s.farmId = :farmId
              AND (s.status = :pending
                   OR (s.status = :retrying
                       AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= :now)))
            ORDER BY s.submittedAt ASC
            """)
    List<StagedFeedingEvent> findDueForProcessing(
            @Param("farmId") UUID farmId,
            @Param("now") LocalDateTime now,
            @Param("pending") StagedStatus pending,
            @Param("retrying") StagedStatus retrying,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT s.farmId FROM StagedFeedingEvent s
            WHERE s.status = :pending
            OR (s.status = :retrying AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= :now))
            """)
    List<UUID> findDistinctFarmIdsWithDueRows(
            @Param("now") LocalDateTime now,
            @Param("pending") StagedStatus pending,
            @Param("retrying") StagedStatus retrying);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StagedFeedingEvent s WHERE s.id = :id")
    Optional<StagedFeedingEvent> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM StagedFeedingEvent s WHERE s.farmId = :farmId")
    int deleteByFarmId(@Param("farmId") UUID farmId);
}