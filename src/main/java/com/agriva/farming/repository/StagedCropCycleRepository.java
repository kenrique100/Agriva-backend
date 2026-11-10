package com.agriva.farming.repository;

import com.agriva.farming.model.entity.StagedCropCycle;
import com.agriva.farming.model.entity.StagedCropCycleId;
import com.agriva.farming.model.enums.StagedCropCycleStatus;
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
public interface StagedCropCycleRepository extends JpaRepository<StagedCropCycle, StagedCropCycleId> {

    Page<StagedCropCycle> findByFarmId(UUID farmId, Pageable pageable);
    Page<StagedCropCycle> findByFarmIdAndStatus(UUID farmId, StagedCropCycleStatus status, Pageable pageable);
    long countByFarmIdAndStatus(UUID farmId, StagedCropCycleStatus status);

    @Query("""
            SELECT s FROM StagedCropCycle s
            WHERE s.status = :pending
               OR (s.status = :retrying
                   AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= :now))
            ORDER BY s.submittedAt ASC
            """)
    List<StagedCropCycle> findDueForProcessing(
            @Param("now") LocalDateTime now,
            @Param("pending") StagedCropCycleStatus pending,
            @Param("retrying") StagedCropCycleStatus retrying,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StagedCropCycle s WHERE s.id = :id")
    Optional<StagedCropCycle> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT s FROM StagedCropCycle s WHERE s.id = :id ORDER BY s.submittedAt DESC")
    List<StagedCropCycle> findAllByEventId(@Param("id") UUID id);

    default Optional<StagedCropCycle> findByIdOnly(UUID id) {
        return findAllByEventId(id).stream().findFirst();
    }

    @Modifying
    @Query("DELETE FROM StagedCropCycle s WHERE s.farmId = :farmId")
    int deleteByFarmId(@Param("farmId") UUID farmId);
}