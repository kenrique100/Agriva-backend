package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.FeedStoreAdjustment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FeedStoreAdjustmentRepository
        extends JpaRepository<FeedStoreAdjustment, UUID> {

    Page<FeedStoreAdjustment> findByFarmIdOrderByPerformedAtDesc(UUID farmId, Pageable pageable);
}