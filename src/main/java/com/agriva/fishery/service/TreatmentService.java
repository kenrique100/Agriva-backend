package com.agriva.fishery.service;

import com.agriva.fishery.model.dto.request.TreatmentRequest;
import com.agriva.fishery.model.dto.response.TreatmentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TreatmentService {

    TreatmentResponse record(UUID pondId, TreatmentRequest request, UUID farmId, UUID userId);

    Page<TreatmentResponse> listForPond(UUID pondId, UUID farmId, Pageable pageable);

    void delete(UUID pondId, UUID treatmentId, UUID farmId);
}