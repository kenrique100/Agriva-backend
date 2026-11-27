package com.agriva.feeding.service;

import com.agriva.feeding.model.dto.request.DemandForecastRequest;
import com.agriva.feeding.model.dto.request.ProduceFeedFromForecastRequest;
import com.agriva.feeding.model.dto.request.UpdateDemandForecastRequest;
import com.agriva.feeding.model.dto.response.DemandForecastResponse;
import com.agriva.feeding.model.dto.response.ProduceFeedFromForecastResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ForecastService {

    DemandForecastResponse createForecast(DemandForecastRequest request, UUID farmId, UUID userId);

    Page<DemandForecastResponse> listForecasts(UUID farmId, Pageable pageable);

    DemandForecastResponse getForecast(UUID id, UUID farmId);

    DemandForecastResponse updateForecast(UUID id,
                                          UpdateDemandForecastRequest request,
                                          UUID farmId,
                                          UUID userId);

    void deleteForecast(UUID id, UUID farmId, UUID userId);

    ProduceFeedFromForecastResponse produceFeedFromForecast(
            UUID forecastId,
            ProduceFeedFromForecastRequest request,
            UUID farmId,
            UUID userId,
            String idempotencyKey);

    ProduceFeedFromForecastResponse replayByKey(UUID farmId, String idempotencyKey);
}