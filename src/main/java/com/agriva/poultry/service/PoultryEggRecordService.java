package com.agriva.poultry.service;

import com.agriva.poultry.model.dto.request.EggRecordRequest;
import com.agriva.poultry.model.dto.response.EggRecordResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PoultryEggRecordService {

    EggRecordResponse record(UUID batchId, EggRecordRequest request,
                             UUID farmId, UUID userId);

    Page<EggRecordResponse> listForBatch(UUID batchId, UUID farmId, Pageable pageable);
}