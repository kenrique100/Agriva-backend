package com.agriva.repository;

import com.agriva.model.entity.CropCycle;
import com.agriva.model.entity.enums.CropCycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.*;
public interface CropCycleRepository extends JpaRepository<CropCycle, UUID> {
    @EntityGraph(attributePaths = {"field", "crop"})
    List<CropCycle> findByFarmId(UUID farmId);

    @EntityGraph(attributePaths = {"field", "crop"})
    Optional<CropCycle> findByIdAndFarmId(UUID id, UUID farmId);

    List<CropCycle> findByFarmIdAndStatus(UUID farmId, CropCycleStatus status);
}