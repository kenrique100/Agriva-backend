package com.agriva.poultry.service;

import com.agriva.poultry.model.dto.request.PoultryBatchRequest;
import com.agriva.poultry.model.dto.response.PoultryBatchResponse;
import com.agriva.poultry.model.enums.PoultryBatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PoultryBatchService {

    PoultryBatchResponse createBatch(UUID houseId, PoultryBatchRequest request,
                                     UUID farmId, UUID userId);

    List<PoultryBatchResponse> listBatchesByHouse(UUID houseId, UUID farmId);

    Page<PoultryBatchResponse> listBatchesByFarm(UUID farmId, Pageable pageable);

    PoultryBatchResponse getBatch(UUID id, UUID farmId);

    PoultryBatchResponse updateStatus(UUID id, PoultryBatchStatus status, UUID farmId);
}