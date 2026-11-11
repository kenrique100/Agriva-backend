package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.FormulationLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FormulationLineRepository extends JpaRepository<FormulationLine, UUID> {

    List<FormulationLine> findByFormulationIdOrderByPercentageDesc(UUID formulationId);

    void deleteByFormulationId(UUID formulationId);
}