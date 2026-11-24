package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.StagedFeedingIngredient;
import com.agriva.feeding.model.entity.StagedFeedingIngredientId;
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
public interface StagedFeedingIngredientRepository
        extends JpaRepository<StagedFeedingIngredient, StagedFeedingIngredientId> {

    Page<StagedFeedingIngredient> findByFarmId(UUID farmId, Pageable pageable);
    Page<StagedFeedingIngredient> findByFarmIdAndStatus(UUID farmId, StagedStatus status, Pageable pageable);
    long countByFarmIdAndStatus(UUID farmId, StagedStatus status);

    @Query("""
            SELECT s FROM StagedFeedingIngredient s
            WHERE s.farmId = :farmId AND s.batchId = :batchId
            ORDER BY s.rowNumber ASC, s.submittedAt ASC
            """)
    List<StagedFeedingIngredient> findByFarmIdAndBatchIdOrderByRowNumberAsc(
            @Param("farmId") UUID farmId,
            @Param("batchId") UUID batchId);

    @Query("""
            SELECT s FROM StagedFeedingIngredient s
            WHERE s.status = :pending
            OR (s.status = :retrying AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= :now))
            ORDER BY s.submittedAt ASC
            """)
    List<StagedFeedingIngredient> findDueForProcessing(
            @Param("now") LocalDateTime now,
            @Param("pending") StagedStatus pending,
            @Param("retrying") StagedStatus retrying,
            Pageable pageable);

    @Query("""
            SELECT s FROM StagedFeedingIngredient s
            WHERE s.farmId = :farmId
              AND (s.status = :pending
                   OR (s.status = :retrying
                       AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= :now)))
            ORDER BY s.submittedAt ASC
            """)
    List<StagedFeedingIngredient> findDueForProcessing(
            @Param("farmId") UUID farmId,
            @Param("now") LocalDateTime now,
            @Param("pending") StagedStatus pending,
            @Param("retrying") StagedStatus retrying,
            Pageable pageable);

    @Query("""
            SELECT DISTINCT s.farmId FROM StagedFeedingIngredient s
            WHERE s.status = :pending
            OR (s.status = :retrying AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= :now))
            """)
    List<UUID> findDistinctFarmIdsWithDueRows(
            @Param("now") LocalDateTime now,
            @Param("pending") StagedStatus pending,
            @Param("retrying") StagedStatus retrying);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StagedFeedingIngredient s WHERE s.id = :id")
    Optional<StagedFeedingIngredient> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT s FROM StagedFeedingIngredient s WHERE s.id = :id ORDER BY s.submittedAt DESC")
    List<StagedFeedingIngredient> findAllByEventId(@Param("id") UUID id);

    default Optional<StagedFeedingIngredient> findByIdOnly(UUID id) {
        return findAllByEventId(id).stream().findFirst();
    }

    @Modifying
    @Query("DELETE FROM StagedFeedingIngredient s WHERE s.farmId = :farmId")
    int deleteByFarmId(@Param("farmId") UUID farmId);
}