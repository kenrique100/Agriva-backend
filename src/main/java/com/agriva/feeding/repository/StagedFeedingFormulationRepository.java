package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.StagedFeedingFormulation;
import com.agriva.feeding.model.entity.StagedFeedingFormulationId;
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
public interface StagedFeedingFormulationRepository
        extends JpaRepository<StagedFeedingFormulation, StagedFeedingFormulationId> {

    Page<StagedFeedingFormulation> findByFarmId(UUID farmId, Pageable pageable);

    Page<StagedFeedingFormulation> findByFarmIdAndStatus(UUID farmId,
                                                         StagedStatus status,
                                                         Pageable pageable);

    long countByFarmIdAndStatus(UUID farmId, StagedStatus status);

    @Query("""
            SELECT s FROM StagedFeedingFormulation s
            WHERE s.status = :pending
               OR (s.status = :retrying
                   AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= :now))
            ORDER BY s.submittedAt ASC
            """)
    List<StagedFeedingFormulation> findDueForProcessing(
            @Param("now") LocalDateTime now,
            @Param("pending") StagedStatus pending,
            @Param("retrying") StagedStatus retrying,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StagedFeedingFormulation s WHERE s.id = :id")
    Optional<StagedFeedingFormulation> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("DELETE FROM StagedFeedingFormulation s WHERE s.farmId = :farmId")
    int deleteByFarmId(@Param("farmId") UUID farmId);
}