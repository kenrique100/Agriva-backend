package com.agriva.feeding.service;

import com.agriva.feeding.model.dto.request.DemandForecastRequest;
import com.agriva.feeding.model.dto.response.DemandForecastResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ForecastService {

    DemandForecastResponse createForecast(DemandForecastRequest request, UUID farmId, UUID userId);

    Page<DemandForecastResponse> listForecasts(UUID farmId, Pageable pageable);

    DemandForecastResponse getForecast(UUID id, UUID farmId);
}