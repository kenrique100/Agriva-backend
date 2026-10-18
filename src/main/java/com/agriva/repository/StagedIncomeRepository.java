package com.agriva.repository;

import com.agriva.model.entity.StagedIncome;
import com.agriva.model.entity.StagedIncomeStatus;
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
public interface StagedIncomeRepository extends JpaRepository<StagedIncome, UUID> {

    Page<StagedIncome> findByFarmId(UUID farmId, Pageable pageable);

    Page<StagedIncome> findByFarmIdAndStatus(UUID farmId,
                                             StagedIncomeStatus status,
                                             Pageable pageable);

    long countByFarmIdAndStatus(UUID farmId, StagedIncomeStatus status);

    @Query("""
            SELECT s FROM StagedIncome s
            WHERE s.status = com.agriva.model.entity.StagedIncomeStatus.PENDING
               OR (s.status = com.agriva.model.entity.StagedIncomeStatus.RETRYING
                   AND (s.nextRetryAt IS NULL OR s.nextRetryAt <= :now))
            ORDER BY s.submittedAt ASC
            """)
    List<StagedIncome> findDueForProcessing(@Param("now") LocalDateTime now,
                                            Pageable pageable);
}