package com.agriva.repository;

import com.agriva.model.entity.StagedSalary;
import com.agriva.model.entity.enums.StagedSalaryStatus;
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
}