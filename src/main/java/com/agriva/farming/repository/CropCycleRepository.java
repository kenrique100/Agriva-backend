package com.agriva.farming.repository;

import com.agriva.farming.model.entity.CropCycle;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CropCycleRepository extends JpaRepository<CropCycle, UUID> {

    @EntityGraph(attributePaths = {"field", "crop"})
    @Query("SELECT c FROM CropCycle c WHERE c.farmId = :farmId AND c.deletedAt IS NULL")
    List<CropCycle> findByFarmIdLive(@Param("farmId") UUID farmId);

    @EntityGraph(attributePaths = {"field", "crop"})
    @Query("SELECT c FROM CropCycle c WHERE c.id = :id AND c.farmId = :farmId " +
            "AND c.deletedAt IS NULL")
    Optional<CropCycle> findByIdAndFarmIdLive(@Param("id") UUID id,
                                              @Param("farmId") UUID farmId);
}