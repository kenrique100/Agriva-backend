package com.agriva.fishery.service;

import com.agriva.fishery.model.dto.request.MortalityRequest;
import com.agriva.fishery.model.dto.response.MortalityResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface MortalityService {

    MortalityResponse record(UUID pondId, MortalityRequest request, UUID farmId, UUID userId);

    Page<MortalityResponse> listForPond(UUID pondId, UUID farmId, Pageable pageable);
}