package com.agriva.poultry.repository;

import com.agriva.poultry.model.entity.PoultryEggRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface PoultryEggRecordRepository extends JpaRepository<PoultryEggRecord, UUID> {

    @Query("SELECT e FROM PoultryEggRecord e WHERE e.batchId = :batchId " +
            "ORDER BY e.recordedAt DESC, e.createdAt DESC")
    Page<PoultryEggRecord> findByBatchId(@Param("batchId") UUID batchId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(e.eggsCollected), 0) FROM PoultryEggRecord e " +
            "WHERE e.batchId = :batchId AND e.recordedAt BETWEEN :from AND :to")
    long sumEggsInRange(@Param("batchId") UUID batchId,
                        @Param("from") LocalDate from,
                        @Param("to") LocalDate to);
}