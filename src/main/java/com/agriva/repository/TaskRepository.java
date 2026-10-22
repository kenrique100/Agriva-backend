package com.agriva.repository;

import com.agriva.model.entity.Task;
import com.agriva.model.entity.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    List<Task> findByPersonnelId(UUID personnelId);

    List<Task> findByPersonnelIdAndIsValidated(UUID personnelId, Boolean isValidated);

    List<Task> findByDeadlineBeforeAndStatus(LocalDate deadline, TaskStatus status);

    List<Task> findByStatus(TaskStatus status);

    @EntityGraph(attributePaths = {"personnel"})
    Page<Task> findByFarmId(UUID farmId, Pageable pageable);

    @EntityGraph(attributePaths = {"personnel"})
    Page<Task> findByFarmIdAndPersonnelId(UUID farmId, UUID personnelId, Pageable pageable);

    @EntityGraph(attributePaths = {"personnel"})
    Page<Task> findByFarmIdAndStatus(UUID farmId, TaskStatus status, Pageable pageable);

    // Also (used by taskService.action / validate / get):
    @EntityGraph(attributePaths = {"personnel"})
    Optional<Task> findByIdAndFarmId(UUID id, UUID farmId);

    @Query("""
            SELECT t FROM Task t
            WHERE t.status = :status
              AND t.isSubmitted = true
              AND t.stopTime BETWEEN :start AND :end
            """)
    List<Task> findSubmittedTasksWithStopTimeBetween(
            @Param("status") TaskStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            SELECT t FROM Task t
            WHERE t.personnel.id = :personnelId
              AND t.status = :status
              AND t.validationTime BETWEEN :start AND :end
            """)
    List<Task> findByPersonnelIdAndStatusAndValidationTimeBetween(
            @Param("personnelId") UUID personnelId,
            @Param("status") TaskStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    void deleteByPersonnelId(UUID personnelId);

    long countByFarmIdAndStatus(UUID farmId, TaskStatus status);
}