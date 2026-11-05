package com.agriva.finance.service;

import com.agriva.finance.model.dto.request.TransactionRequest;
import com.agriva.finance.model.dto.response.TransactionResponse;
import com.agriva.finance.model.dto.response.TransactionSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TransactionService {

    TransactionResponse create(TransactionRequest request, UUID farmId, UUID userId);

    Page<TransactionResponse> list(UUID farmId, Pageable pageable);

    TransactionResponse get(UUID id, UUID farmId);

    TransactionResponse update(UUID id, TransactionRequest request, UUID farmId);

    void delete(UUID id, UUID farmId);

    TransactionSummaryResponse summary(UUID id, UUID farmId);

    int backfillFromIncome(UUID farmId, UUID userId);
}