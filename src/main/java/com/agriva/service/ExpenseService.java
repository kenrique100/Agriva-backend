package com.agriva.service;

import com.agriva.model.dto.common.CursorPageResponse;
import com.agriva.model.dto.expense.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

public interface ExpenseService {

    ExpenseSubmissionAck submitExpense(ExpenseRequest request, UUID farmId, UUID userId, String email);

    ExpenseBulkUploadResponse bulkUpload(MultipartFile file, UUID farmId, UUID userId, String email);

    Page<ExpenseResponse> listExpenses(UUID farmId, Pageable pageable);

    /** Keyset (cursor) pagination. */
    CursorPageResponse<ExpenseResponse> listExpensesByCursor(UUID farmId, String cursor, int size);

    ExpenseResponse getExpense(UUID id, UUID farmId);

    ExpenseResponse updateExpense(UUID id, ExpenseRequest request, UUID farmId);

    void deleteExpense(UUID id, UUID farmId);

    Page<ExpenseStagingResponse> listStaging(UUID farmId, String status, Pageable pageable);

    Map<String, Long> stagingStats(UUID farmId);

    ExpenseResponse createDirect(ExpenseRequest request, UUID farmId, UUID userId);
}