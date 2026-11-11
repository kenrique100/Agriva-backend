package com.agriva.feeding.service;

import com.agriva.feeding.model.dto.request.ProductionRequest;
import com.agriva.feeding.model.dto.response.FarmStoreResponse;
import com.agriva.feeding.model.dto.response.FeedBatchResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ProductionService {

    FeedBatchResponse produce(ProductionRequest request, UUID farmId, UUID userId);

    Page<FeedBatchResponse> listBatches(UUID farmId, Pageable pageable);

    FarmStoreResponse getStore(UUID farmId);
}