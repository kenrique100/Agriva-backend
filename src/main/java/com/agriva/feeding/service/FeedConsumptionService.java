package com.agriva.feeding.service;

import com.agriva.feeding.model.dto.request.FeedingRequest;
import com.agriva.feeding.model.dto.response.FeedingEventResponse;
import com.agriva.feeding.port.ConsumerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface FeedConsumptionService {

    FeedingEventResponse feed(FeedingRequest request, UUID farmId, UUID userId);

    Page<FeedingEventResponse> listFeedings(UUID farmId,
                                            ConsumerType consumerType,
                                            UUID consumerId,
                                            Pageable pageable);
}