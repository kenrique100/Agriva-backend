package com.agriva.service;

import com.agriva.model.dto.salary.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

public interface SalaryService {
    SalaryPaymentResponse create(SalaryPaymentRequest request, UUID farmId, UUID actorUserId);
    Page<SalaryPaymentResponse> list(UUID farmId, UUID personnelId, Pageable pageable);
    SalaryPaymentResponse get(UUID id, UUID farmId);
    void delete(UUID id, UUID farmId);
    SalaryReceiptResponse getReceipt(UUID id, UUID farmId);
    byte[] getReceiptPdf(UUID id, UUID farmId);
    SalaryBulkUploadResponse bulkUpload(MultipartFile file, UUID farmId, UUID userId, String email);
    Page<StagedSalaryResponse> listStaging(UUID farmId, String status, Pageable pageable);
    Map<String, Long> stagingStats(UUID farmId);
}