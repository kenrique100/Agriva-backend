package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.FeedBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeedBatchRepository extends JpaRepository<FeedBatch, UUID> {

    @Query("SELECT b FROM FeedBatch b WHERE b.farmId = :farmId")
    Page<FeedBatch> findByFarmId(@Param("farmId") UUID farmId, Pageable pageable);

    @Query("SELECT b FROM FeedBatch b WHERE b.id = :id AND b.farmId = :farmId")
    Optional<FeedBatch> findByIdAndFarmId(@Param("id") UUID id, @Param("farmId") UUID farmId);

    long countByFarmId(UUID farmId);
}