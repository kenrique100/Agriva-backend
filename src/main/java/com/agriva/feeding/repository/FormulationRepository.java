package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.Formulation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FormulationRepository extends JpaRepository<Formulation, UUID> {

    @Query("SELECT f FROM Formulation f WHERE f.farmId = :farmId AND f.deletedAt IS NULL")
    Page<Formulation> findByFarmIdLive(@Param("farmId") UUID farmId, Pageable pageable);

    @Query("SELECT f FROM Formulation f WHERE f.id = :id AND f.farmId = :farmId " +
            "AND f.deletedAt IS NULL")
    Optional<Formulation> findByIdAndFarmIdLive(@Param("id") UUID id, @Param("farmId") UUID farmId);

    /** NEW (DR-6): resolve a raw formulation name to a Formulation on this farm. */
    @Query("SELECT f FROM Formulation f WHERE f.farmId = :farmId " +
            "AND LOWER(f.name) = LOWER(:name) AND f.deletedAt IS NULL")
    Optional<Formulation> findByFarmIdAndNameLive(@Param("farmId") UUID farmId,
                                                  @Param("name") String name);

    @Modifying
    @Query("UPDATE Formulation f SET f.deletedAt = :now " +
            "WHERE f.farmId = :farmId AND f.deletedAt IS NULL")
    int softDeleteByFarmId(@Param("farmId") UUID farmId, @Param("now") LocalDateTime now);
}