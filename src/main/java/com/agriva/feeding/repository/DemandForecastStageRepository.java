package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.DemandForecastStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DemandForecastStageRepository extends JpaRepository<DemandForecastStage, UUID> {

    List<DemandForecastStage> findByForecastIdOrderByStageIndexAsc(UUID forecastId);

    List<DemandForecastStage> findByForecastIdInOrderByForecastIdAscStageIndexAsc(
            Collection<UUID> forecastIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DemandForecastStage s WHERE s.forecastId = :forecastId")
    int deleteAllByForecastId(@Param("forecastId") UUID forecastId);
}