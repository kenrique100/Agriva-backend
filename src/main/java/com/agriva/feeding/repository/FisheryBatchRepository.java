package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.FisheryBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FisheryBatchRepository extends JpaRepository<FisheryBatch, UUID> {

    List<FisheryBatch> findByFarmIdAndPondId(UUID farmId, UUID pondId);

    Optional<FisheryBatch> findByIdAndFarmId(UUID id, UUID farmId);
}