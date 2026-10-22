package com.agriva.repository;

import com.agriva.model.entity.StagedTask;
import com.agriva.model.entity.enums.StagedTaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StagedTaskRepository extends JpaRepository<StagedTask, UUID> {

    Page<StagedTask> findByFarmId(UUID farmId, Pageable pageable);

    Page<StagedTask> findByFarmIdAndStatus(UUID farmId, StagedTaskStatus status, Pageable pageable);

    long countByFarmIdAndStatus(UUID farmId, StagedTaskStatus status);

    @Query("""
            SELECT s FROM StagedTask s
            WHERE s.status = :pending
               OR (s.status = :retrying
                   AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= :now))
            ORDER BY s.submittedAt ASC
            """)
    List<StagedTask> findDueForProcessing(
            @Param("now") LocalDateTime now,
            @Param("pending") StagedTaskStatus pending,
            @Param("retrying") StagedTaskStatus retrying,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StagedTask s WHERE s.id = :id")
    Optional<StagedTask> findByIdForUpdate(@Param("id") UUID id);
}