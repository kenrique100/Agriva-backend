package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.DemandForecastStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DemandForecastStageRepository extends JpaRepository<DemandForecastStage, UUID> {

    List<DemandForecastStage> findByForecastIdOrderByStageIndexAsc(UUID forecastId);
}