package com.agriva.finance.service;

import com.agriva.finance.model.dto.request.IncomeRequest;
import com.agriva.finance.model.dto.response.BatchStatusResponse;
import com.agriva.finance.model.dto.response.BatchUploadResponse;
import com.agriva.finance.model.dto.response.IncomeResponse;
import com.agriva.finance.model.dto.response.IncomeStagingResponse;
import com.agriva.finance.model.dto.response.IncomeSubmissionAck;
import com.agriva.shared.models.dto.CursorPageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

public interface IncomeService {

    IncomeSubmissionAck submitIncome(IncomeRequest request, UUID farmId, UUID userId, String email);

    BatchUploadResponse bulkUpload(MultipartFile file, UUID farmId, UUID userId, String email);

    BatchStatusResponse bulkStatus(UUID batchId, UUID farmId);

    Page<IncomeResponse> listIncome(UUID farmId, Pageable pageable);

    CursorPageResponse<IncomeResponse> listIncomeByCursor(UUID farmId, String cursor, int size);

    IncomeResponse getIncome(UUID id, UUID farmId);

    IncomeResponse updateIncome(UUID id, IncomeRequest request, UUID farmId);

    void deleteIncome(UUID id, UUID farmId);

    Page<IncomeStagingResponse> listStaging(UUID farmId, String status, Pageable pageable);

    Map<String, Long> stagingStats(UUID farmId);
}