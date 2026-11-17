package com.agriva.poultry.repository;

import com.agriva.poultry.model.entity.PoultryMortalityRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface PoultryMortalityRecordRepository
        extends JpaRepository<PoultryMortalityRecord, UUID> {

    @Query("SELECT m FROM PoultryMortalityRecord m WHERE m.batchId = :batchId " +
            "ORDER BY m.recordedAt DESC, m.createdAt DESC")
    Page<PoultryMortalityRecord> findByBatchId(@Param("batchId") UUID batchId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(m.quantity), 0) FROM PoultryMortalityRecord m " +
            "WHERE m.batchId = :batchId AND m.recordedAt BETWEEN :from AND :to")
    long sumQuantityInRange(@Param("batchId") UUID batchId,
                            @Param("from") LocalDate from,
                            @Param("to") LocalDate to);
}