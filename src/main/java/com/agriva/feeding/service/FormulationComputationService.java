package com.agriva.feeding.service;

import com.agriva.feeding.model.dto.request.FormulationRequest;
import com.agriva.feeding.model.dto.response.FormulationResponse;
import com.agriva.feeding.model.entity.Formulation;
import com.agriva.feeding.model.entity.FormulationLine;

import java.util.List;
import java.util.UUID;

public interface FormulationComputationService {

    record Computed(Formulation formulation, List<FormulationLine> lines) {}

    Computed computeAndPersist(FormulationRequest request,
                               UUID farmId,
                               UUID userId);

    FormulationResponse toResponse(Computed computed);
}