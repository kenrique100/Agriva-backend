package com.agriva.feeding.service;

import com.agriva.feeding.model.dto.request.FormulationRequest;
import com.agriva.feeding.model.dto.response.FormulationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface FormulationService {

    FormulationResponse createFormulation(FormulationRequest request, UUID farmId, UUID userId);

    Page<FormulationResponse> listFormulations(UUID farmId, Pageable pageable);

    FormulationResponse getFormulation(UUID id, UUID farmId);

    void deleteFormulation(UUID id, UUID farmId);
}