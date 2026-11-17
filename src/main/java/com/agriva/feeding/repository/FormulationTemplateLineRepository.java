package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.FormulationTemplateLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FormulationTemplateLineRepository extends JpaRepository<FormulationTemplateLine, UUID> {

    List<FormulationTemplateLine> findByTemplateId(UUID templateId);

    @Query("SELECT COALESCE(SUM(l.percentage), 0) FROM FormulationTemplateLine l " +
            "WHERE l.templateId = :templateId")
    java.math.BigDecimal sumPercentageByTemplateId(@Param("templateId") UUID templateId);
}