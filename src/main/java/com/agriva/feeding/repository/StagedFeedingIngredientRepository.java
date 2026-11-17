package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.StagedFeedingIngredient;
import com.agriva.feeding.model.entity.StagedFeedingIngredientId;
import com.agriva.feeding.model.enums.StagedFeedingStatus;
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

    Page<StagedFeedingIngredient> findByFarmIdAndStatus(UUID farmId,
                                                        StagedFeedingStatus status,
                                                        Pageable pageable);

    long countByFarmIdAndStatus(UUID farmId, StagedFeedingStatus status);

    @Query("""
            SELECT s FROM StagedFeedingIngredient s
            WHERE s.status = :pending
               OR (s.status = :retrying
                   AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= :now))
            ORDER BY s.submittedAt ASC
            """)
    List<StagedFeedingIngredient> findDueForProcessing(
            @Param("now") LocalDateTime now,
            @Param("pending") StagedFeedingStatus pending,
            @Param("retrying") StagedFeedingStatus retrying,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StagedFeedingIngredient s WHERE s.id = :id")
    Optional<StagedFeedingIngredient> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM StagedFeedingIngredient s WHERE s.farmId = :farmId")
    int deleteByFarmId(@Param("farmId") UUID farmId);
}