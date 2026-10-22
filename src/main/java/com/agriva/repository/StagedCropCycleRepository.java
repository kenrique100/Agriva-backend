package com.agriva.repository;

import com.agriva.model.entity.StagedCropCycle;
import com.agriva.model.entity.enums.StagedCropCycleStatus;
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
public interface StagedCropCycleRepository extends JpaRepository<StagedCropCycle, UUID> {

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
}