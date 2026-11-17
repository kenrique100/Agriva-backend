package com.agriva.fishery.repository;

import com.agriva.fishery.model.entity.GrowthSample;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GrowthSampleRepository extends JpaRepository<GrowthSample, UUID> {

    @Query("SELECT g FROM GrowthSample g WHERE g.pondId = :pondId")
    Page<GrowthSample> findByPondId(@Param("pondId") UUID pondId, Pageable pageable);

    @Query("SELECT g FROM GrowthSample g WHERE g.farmId = :farmId")
    Page<GrowthSample> findByFarmId(@Param("farmId") UUID farmId, Pageable pageable);

    @Query("SELECT g FROM GrowthSample g WHERE g.pondId = :pondId " +
            "ORDER BY g.sampledAt DESC, g.createdAt DESC")
    java.util.List<GrowthSample> findLatestByPondId(@Param("pondId") UUID pondId, Pageable pageable);

    @Query("SELECT g FROM GrowthSample g WHERE g.pondId = :pondId AND g.species = :species " +
            "ORDER BY g.sampledAt DESC, g.createdAt DESC")
    java.util.List<GrowthSample> findLatestByPondIdAndSpecies(
            @Param("pondId") UUID pondId,
            @Param("species") String species,
            Pageable pageable);

    default Optional<GrowthSample> findTopByPondIdOrderBySampledAtDesc(UUID pondId) {
        return findLatestByPondId(pondId, Pageable.ofSize(1)).stream().findFirst();
    }
}