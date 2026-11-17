package com.agriva.poultry.service;

import com.agriva.poultry.model.dto.request.MortalityRecordRequest;
import com.agriva.poultry.model.dto.response.MortalityRecordResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PoultryMortalityService {

    MortalityRecordResponse record(UUID batchId, MortalityRecordRequest request,
                                   UUID farmId, UUID userId);

    Page<MortalityRecordResponse> listForBatch(UUID batchId, UUID farmId, Pageable pageable);
}