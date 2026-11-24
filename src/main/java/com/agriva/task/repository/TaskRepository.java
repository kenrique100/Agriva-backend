package com.agriva.task.repository;

import com.agriva.task.model.entity.Task;
import com.agriva.task.model.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    @Query("SELECT t FROM Task t WHERE t.farmId = :farmId AND t.deletedAt IS NULL")
    Page<Task> findByFarmIdLive(@Param("farmId") UUID farmId, Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.farmId = :farmId AND t.status = :status " +
            "AND t.deletedAt IS NULL")
    Page<Task> findByFarmIdAndStatusLive(@Param("farmId") UUID farmId,
                                         @Param("status") TaskStatus status,
                                         Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.farmId = :farmId AND t.personnelId = :personnelId " +
            "AND t.deletedAt IS NULL")
    Page<Task> findByFarmIdAndPersonnelIdLive(@Param("farmId") UUID farmId,
                                              @Param("personnelId") UUID personnelId,
                                              Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.farmId = :farmId AND t.personnelId = :personnelId " +
            "AND t.status = :status AND t.deletedAt IS NULL")
    Page<Task> findByFarmIdAndPersonnelIdAndStatusLive(@Param("farmId") UUID farmId,
                                                       @Param("personnelId") UUID personnelId,
                                                       @Param("status") TaskStatus status,
                                                       Pageable pageable);

    @Query("SELECT t FROM Task t WHERE t.id = :id AND t.farmId = :farmId AND t.deletedAt IS NULL")
    Optional<Task> findByIdAndFarmIdLive(@Param("id") UUID id, @Param("farmId") UUID farmId);

    @Query("SELECT t FROM Task t WHERE t.personnelId = :personnelId AND t.deletedAt IS NULL")
    List<Task> findByPersonnelIdLive(@Param("personnelId") UUID personnelId);

    @Query("SELECT t FROM Task t WHERE t.personnelId = :personnelId " +
            "AND t.isValidated = :validated AND t.deletedAt IS NULL")
    List<Task> findByPersonnelIdAndIsValidatedLive(@Param("personnelId") UUID personnelId,
                                                   @Param("validated") Boolean validated);

    @Query("SELECT t FROM Task t WHERE t.personnelId = :personnelId " +
            "AND t.isValidated = true AND t.deletedAt IS NULL " +
            "AND t.validationTime >= :since")
    List<Task> findValidatedSince(@Param("personnelId") UUID personnelId,
                                  @Param("since") LocalDateTime since);

    @Query("SELECT t FROM Task t WHERE t.deadline < :deadline AND t.status = :status " +
            "AND t.deletedAt IS NULL")
    List<Task> findByDeadlineBeforeAndStatus(@Param("deadline") LocalDate deadline,
                                             @Param("status") TaskStatus status);

    @Query("SELECT t FROM Task t WHERE t.status = :status AND t.isSubmitted = true " +
            "AND t.stopTime BETWEEN :start AND :end AND t.deletedAt IS NULL")
    List<Task> findSubmittedTasksWithStopTimeBetween(
            @Param("status") TaskStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT t FROM Task t WHERE t.status = :status AND t.isSubmitted = true " +
            "AND t.stopTime < :before AND t.deletedAt IS NULL")
    List<Task> findSubmittedTasksWithStopTimeBefore(
            @Param("status") TaskStatus status,
            @Param("before") LocalDateTime before);

    @Query("SELECT t FROM Task t WHERE t.personnelId = :personnelId " +
            "AND t.status = :status AND t.validationTime BETWEEN :start AND :end " +
            "AND t.deletedAt IS NULL")
    List<Task> findByPersonnelIdAndStatusAndValidationTimeBetween(
            @Param("personnelId") UUID personnelId,
            @Param("status") TaskStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query(value = """
        SELECT t.* FROM tasks t
        WHERE t.status = 'IN_PROGRESS'
          AND t.deleted_at IS NULL
          AND t.last_resume_time IS NOT NULL
          AND t.expected_hours IS NOT NULL
          AND t.expected_hours > 0
          AND (COALESCE(t.total_worked_minutes, 0)
               + EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - t.last_resume_time)) / 60)
              >= t.expected_hours * 60
    """, nativeQuery = true)
    List<Task> findTasksDueForAutoStop();

    @Query("""
        SELECT t.personnelId,
               SUM(CASE WHEN t.actualHours IS NOT NULL THEN t.actualHours ELSE 0 END),
               SUM(CASE WHEN t.expectedHours IS NOT NULL THEN t.expectedHours ELSE 0 END),
               COUNT(t)
        FROM Task t
        WHERE t.farmId = :farmId AND t.isValidated = true AND t.deletedAt IS NULL
        GROUP BY t.personnelId
    """)
    List<Object[]> aggregateProductivityByFarm(@Param("farmId") UUID farmId);

    void deleteByPersonnelId(UUID personnelId);
}