package com.agriva.service;

import com.agriva.model.dto.transaction.TransactionRequest;
import com.agriva.model.dto.transaction.TransactionResponse;
import com.agriva.model.dto.transaction.TransactionSummaryResponse;
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