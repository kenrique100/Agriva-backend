package com.agriva.fishery.service;

import com.agriva.fishery.model.dto.request.GrowthSampleRequest;
import com.agriva.fishery.model.dto.response.GrowthSampleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface GrowthSampleService {

    GrowthSampleResponse record(UUID pondId, GrowthSampleRequest request, UUID farmId, UUID userId);

    Page<GrowthSampleResponse> listForPond(UUID pondId, UUID farmId, Pageable pageable);
}