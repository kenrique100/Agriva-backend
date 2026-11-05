package com.agriva.payroll.repository;

import com.agriva.payroll.model.entity.StagedSalary;
import com.agriva.payroll.model.enums.StagedSalaryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StagedSalaryRepository extends JpaRepository<StagedSalary, UUID> {
    Page<StagedSalary> findByFarmId(UUID farmId, Pageable pageable);
    Page<StagedSalary> findByFarmIdAndStatus(UUID farmId, StagedSalaryStatus status, Pageable pageable);
    long countByFarmIdAndStatus(UUID farmId, StagedSalaryStatus status);

    @Query("""
            SELECT s FROM StagedSalary s
            WHERE s.status = :pending
               OR (s.status = :retrying
                   AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= :now))
            ORDER BY s.submittedAt ASC
            """)
    List<StagedSalary> findDueForProcessing(
            @Param("now") LocalDateTime now,
            @Param("pending") StagedSalaryStatus pending,
            @Param("retrying") StagedSalaryStatus retrying,
            Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM StagedSalary s WHERE s.id = :id")
    Optional<StagedSalary> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM StagedSalary s
            WHERE s.id = :id
              AND (s.status = :pending
                   OR (s.status = :retrying
                       AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= :now)))
            """)
    Optional<StagedSalary> findDueByIdForUpdate(
            @Param("id") UUID id,
            @Param("now") LocalDateTime now,
            @Param("pending") StagedSalaryStatus pending,
            @Param("retrying") StagedSalaryStatus retrying);

    @Modifying
    @Query("DELETE FROM StagedSalary s WHERE s.farmId = :farmId")
    int deleteByFarmId(@Param("farmId") UUID farmId);
}