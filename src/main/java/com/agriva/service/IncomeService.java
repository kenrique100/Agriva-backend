package com.agriva.service;

import com.agriva.model.dto.common.CursorPageResponse;
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

    /**
     * Keyset (cursor) pagination. {@code cursor} is null on the first page.
     * The returned envelope contains {@code nextCursor} — pass it back to get
     * the next page; when {@code hasNext} is false you are done.
     */
    CursorPageResponse<IncomeResponse> listIncomeByCursor(UUID farmId, String cursor, int size);

    IncomeResponse getIncome(UUID id, UUID farmId);

    IncomeResponse updateIncome(UUID id, IncomeRequest request, UUID farmId);

    void deleteIncome(UUID id, UUID farmId);

    Page<IncomeStagingResponse> listStaging(UUID farmId, String status, Pageable pageable);

    Map<String, Long> stagingStats(UUID farmId);
}