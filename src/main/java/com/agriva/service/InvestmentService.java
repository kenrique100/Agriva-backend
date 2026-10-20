package com.agriva.service;

import com.agriva.model.dto.investment.InvestmentRequest;
import com.agriva.model.dto.investment.InvestmentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InvestmentService {

    InvestmentResponse createInvestment(InvestmentRequest request, UUID farmId, UUID userId);

    Page<InvestmentResponse> listInvestments(UUID farmId, Pageable pageable);

    InvestmentResponse getInvestment(UUID id, UUID farmId);

    InvestmentResponse updateInvestment(UUID id, InvestmentRequest request, UUID farmId);

    void deleteInvestment(UUID id, UUID farmId);

    InvestmentResponse activate(UUID id, UUID farmId);

    InvestmentResponse deactivate(UUID id, UUID farmId);

    InvestmentResponse getActive(UUID farmId);   // null if none
}