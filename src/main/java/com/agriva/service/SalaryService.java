package com.agriva.service;

import com.agriva.model.dto.salary.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SalaryService {
    SalaryPaymentResponse create(SalaryPaymentRequest request, UUID farmId, UUID actorUserId);
    Page<SalaryPaymentResponse> list(UUID farmId, UUID personnelId, Pageable pageable);
    SalaryPaymentResponse get(UUID id, UUID farmId);
    void delete(UUID id, UUID farmId);
    SalaryReceiptResponse getReceipt(UUID id, UUID farmId);
    byte[] getReceiptPdf(UUID id, UUID farmId);
}