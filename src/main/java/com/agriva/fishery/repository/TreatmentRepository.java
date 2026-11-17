package com.agriva.fishery.repository;

import com.agriva.fishery.model.entity.Treatment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TreatmentRepository extends JpaRepository<Treatment, UUID> {

    @Query("SELECT t FROM Treatment t WHERE t.farmId = :farmId")
    Page<Treatment> findByFarmId(@Param("farmId") UUID farmId, Pageable pageable);

    @Query("SELECT t FROM Treatment t WHERE t.pondId = :pondId")
    Page<Treatment> findByPondId(@Param("pondId") UUID pondId, Pageable pageable);

    @Query("SELECT t FROM Treatment t WHERE t.id = :id AND t.farmId = :farmId")
    Optional<Treatment> findByIdAndFarmId(@Param("id") UUID id, @Param("farmId") UUID farmId);
}