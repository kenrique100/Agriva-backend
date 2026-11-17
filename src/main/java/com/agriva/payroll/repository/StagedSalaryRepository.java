package com.agriva.payroll.repository;

import com.agriva.payroll.model.entity.StagedSalary;
import com.agriva.payroll.model.entity.StagedSalaryId;
import com.agriva.payroll.model.enums.StagedSalaryStatus;
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
public interface StagedSalaryRepository extends JpaRepository<StagedSalary, StagedSalaryId> {

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

    @Query("SELECT s FROM StagedSalary s WHERE s.id = :id ORDER BY s.submittedAt DESC")
    List<StagedSalary> findAllByEventId(@Param("id") UUID id);

    default Optional<StagedSalary> findByIdOnly(UUID id) {
        return findAllByEventId(id).stream().findFirst();
    }

    @Modifying
    @Query("DELETE FROM StagedSalary s WHERE s.farmId = :farmId")
    int deleteByFarmId(@Param("farmId") UUID farmId);
}