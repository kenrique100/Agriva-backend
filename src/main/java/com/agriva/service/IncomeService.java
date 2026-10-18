package com.agriva.service;

import com.agriva.model.dto.income.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

public interface IncomeService {

    IncomeSubmissionAck submitIncome(IncomeRequest request, UUID farmId, UUID userId, String email);

    IncomeBulkUploadResponse bulkUpload(MultipartFile file, UUID farmId, UUID userId, String email);

    Page<IncomeResponse> listIncome(UUID farmId, Pageable pageable);

    IncomeResponse getIncome(UUID id, UUID farmId);

    IncomeResponse updateIncome(UUID id, IncomeRequest request, UUID farmId);

    void deleteIncome(UUID id, UUID farmId);

    Page<IncomeStagingResponse> listStaging(UUID farmId, String status, Pageable pageable);

    Map<String, Long> stagingStats(UUID farmId);
}