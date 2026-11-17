package com.agriva.poultry.service;

import com.agriva.poultry.model.dto.request.WeightSampleRequest;
import com.agriva.poultry.model.dto.response.WeightSampleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PoultryWeightSampleService {

    WeightSampleResponse record(UUID batchId, WeightSampleRequest request,
                                UUID farmId, UUID userId);

    Page<WeightSampleResponse> listForBatch(UUID batchId, UUID farmId, Pageable pageable);
}