package com.agriva.fishery.repository;

import com.agriva.fishery.model.entity.PondStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PondStockRepository extends JpaRepository<PondStock, UUID> {

    List<PondStock> findByPondId(UUID pondId);

    /**
     * Locks the (pond, species) stock row so concurrent mortality records
     * and growth samples on the same pond + species serialize. Mortality
     * decrements {@code quantity}, growth sampling updates
     * {@code average_weight_g}, both under this lock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM PondStock s WHERE s.pondId = :pondId AND s.species = :species")
    Optional<PondStock> findByPondIdAndSpeciesForUpdate(
            @Param("pondId") UUID pondId,
            @Param("species") String species);
}