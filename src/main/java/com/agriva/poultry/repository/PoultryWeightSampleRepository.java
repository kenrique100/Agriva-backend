package com.agriva.poultry.repository;

import com.agriva.poultry.model.entity.PoultryWeightSample;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PoultryWeightSampleRepository
        extends JpaRepository<PoultryWeightSample, UUID> {

    @Query("SELECT s FROM PoultryWeightSample s WHERE s.batchId = :batchId " +
            "ORDER BY s.sampledAt DESC, s.createdAt DESC")
    Page<PoultryWeightSample> findByBatchId(@Param("batchId") UUID batchId, Pageable pageable);

    @Query("SELECT s FROM PoultryWeightSample s WHERE s.batchId = :batchId " +
            "ORDER BY s.sampledAt DESC, s.createdAt DESC")
    List<PoultryWeightSample> findLatestByBatchId(@Param("batchId") UUID batchId, Pageable pageable);

    default Optional<PoultryWeightSample> findFirstByBatchId(UUID batchId) {
        var list = findLatestByBatchId(batchId, Pageable.ofSize(1));
        return list.stream().findFirst();
    }
}