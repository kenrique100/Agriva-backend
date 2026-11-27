package com.agriva.feeding.repository;

import com.agriva.feeding.model.entity.FormulationLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface FormulationLineRepository extends JpaRepository<FormulationLine, UUID> {

    List<FormulationLine> findByFormulationIdOrderByPercentageDesc(UUID formulationId);

    /** Batch load for list endpoints — avoids N+1 across page content. */
    List<FormulationLine> findByFormulationIdInOrderByFormulationIdAscPercentageDesc(
            Collection<UUID> formulationIds);

    void deleteByFormulationId(UUID formulationId);
}