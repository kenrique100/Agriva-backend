package com.agriva.fishery.service;

import com.agriva.fishery.model.dto.request.PondRequest;
import com.agriva.fishery.model.dto.request.PondStockRequest;
import com.agriva.fishery.model.dto.response.PondResponse;
import com.agriva.fishery.model.dto.response.PondStockResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PondService {

    PondResponse createPond(PondRequest request, UUID farmId, UUID userId);

    Page<PondResponse> listPonds(UUID farmId, Pageable pageable);

    PondResponse getPond(UUID id, UUID farmId);

    PondResponse updatePond(UUID id, PondRequest request, UUID farmId);

    void deletePond(UUID id, UUID farmId);

    PondStockResponse addStock(UUID pondId, PondStockRequest request, UUID farmId);

    List<PondStockResponse> getStock(UUID pondId, UUID farmId);
}