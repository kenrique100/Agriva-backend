package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.FormulationTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FormulationTemplateRepository extends JpaRepository<FormulationTemplate, UUID> {

    @Query("SELECT t FROM FormulationTemplate t " +
            "WHERE t.farmId IS NULL AND t.deletedAt IS NULL")
    List<FormulationTemplate> findSystemTemplatesLive();

    @Query("SELECT t FROM FormulationTemplate t " +
            "WHERE t.farmId = :farmId AND t.deletedAt IS NULL")
    List<FormulationTemplate> findFarmTemplatesLive(@Param("farmId") UUID farmId);

    @Query("SELECT t FROM FormulationTemplate t " +
            "WHERE t.id = :id AND (t.farmId IS NULL OR t.farmId = :farmId) AND t.deletedAt IS NULL")
    java.util.Optional<FormulationTemplate> findByIdOrSystemForFarm(
            @Param("id") UUID id, @Param("farmId") UUID farmId);
}