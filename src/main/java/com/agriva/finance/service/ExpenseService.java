package com.agriva.finance.service;

import com.agriva.finance.model.dto.request.ExpenseRequest;
import com.agriva.finance.model.dto.response.ExpenseResponse;
import com.agriva.finance.model.dto.response.ExpenseStagingResponse;
import com.agriva.finance.model.dto.response.ExpenseSubmissionAck;
import com.agriva.shared.models.dto.BatchStatusResponse;
import com.agriva.shared.models.dto.BatchUploadResponse;
import com.agriva.shared.models.dto.CursorPageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

public interface ExpenseService {

    ExpenseSubmissionAck submitExpense(ExpenseRequest request, UUID farmId, UUID userId, String email);

    BatchUploadResponse bulkUpload(MultipartFile file, UUID farmId, UUID userId, String email);

    BatchStatusResponse bulkStatus(UUID batchId, UUID farmId);

    Page<ExpenseResponse> listExpenses(UUID farmId, Pageable pageable);

    CursorPageResponse<ExpenseResponse> listExpensesByCursor(UUID farmId, String cursor, int size);

    ExpenseResponse getExpense(UUID id, UUID farmId);

    ExpenseResponse updateExpense(UUID id, ExpenseRequest request, UUID farmId);

    void deleteExpense(UUID id, UUID farmId);

    Page<ExpenseStagingResponse> listStaging(UUID farmId, String status, Pageable pageable);

    Map<String, Long> stagingStats(UUID farmId);

    ExpenseResponse createDirect(ExpenseRequest request, UUID farmId, UUID userId);
}