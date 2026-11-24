package com.agriva.task.repository;

import com.agriva.task.model.entity.StagedTask;
import com.agriva.task.model.entity.StagedTaskId;
import com.agriva.task.model.enums.StagedTaskStatus;
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
public interface StagedTaskRepository extends JpaRepository<StagedTask, StagedTaskId> {

    Page<StagedTask> findByFarmId(UUID farmId, Pageable pageable);

    Page<StagedTask> findByFarmIdAndStatus(UUID farmId,
                                           StagedTaskStatus status,
                                           Pageable pageable);

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
    @Query("SELECT s FROM StagedTask s WHERE s.id = :id ORDER BY s.submittedAt DESC")
    List<StagedTask> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT s FROM StagedTask s WHERE s.id = :id ORDER BY s.submittedAt DESC")
    List<StagedTask> findAllByEventId(@Param("id") UUID id);

    default Optional<StagedTask> findByIdOnly(UUID id) {
        return findAllByEventId(id).stream().findFirst();
    }

    @Modifying
    @Query("DELETE FROM StagedTask s WHERE s.farmId = :farmId")
    int deleteByFarmId(@Param("farmId") UUID farmId);

    @Modifying
    @Query("UPDATE StagedTask s SET s.movedTaskId = null WHERE s.movedTaskId = :taskId")
    int clearMovedTaskId(@Param("taskId") UUID taskId);
}