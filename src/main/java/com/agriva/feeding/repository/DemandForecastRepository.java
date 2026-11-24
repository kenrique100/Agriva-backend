package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.DemandForecast;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DemandForecastRepository extends JpaRepository<DemandForecast, UUID> {

    @Query("SELECT f FROM DemandForecast f " +
            "WHERE f.farmId = :farmId AND f.deletedAt IS NULL")
    Page<DemandForecast> findByFarmIdLive(@Param("farmId") UUID farmId, Pageable pageable);

    @Query("SELECT f FROM DemandForecast f " +
            "WHERE f.id = :id AND f.farmId = :farmId AND f.deletedAt IS NULL")
    Optional<DemandForecast> findByIdAndFarmIdLive(@Param("id") UUID id, @Param("farmId") UUID farmId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM DemandForecast f " +
            "WHERE f.id = :id AND f.farmId = :farmId AND f.deletedAt IS NULL")
    Optional<DemandForecast> findByIdAndFarmIdLiveForUpdate(
            @Param("id") UUID id, @Param("farmId") UUID farmId);
}