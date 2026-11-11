package com.agriva.feeding.mapper;

import com.agriva.feeding.model.dto.response.FeedingEventResponse;
import com.agriva.feeding.model.entity.FeedingEvent;
import org.springframework.stereotype.Component;

@Component
public class FeedFeedingMapper {

    public FeedingEventResponse toResponse(FeedingEvent e) {
        return FeedingEventResponse.builder()
                .id(e.getId())
                .farmId(e.getFarmId())
                .consumerType(e.getConsumerType())
                .consumerId(e.getConsumerId())
                .consumerDisplayName(e.getConsumerDisplayName())
                .consumerHeadcount(e.getConsumerHeadcount())
                .consumerStage(e.getConsumerStage())
                .feedBatchId(e.getFeedBatchId())
                .quantityKg(e.getQuantityKg())
                .fedAt(e.getFedAt())
                .notes(e.getNotes())
                .createdByUserId(e.getCreatedByUserId())
                .createdAt(e.getCreatedAt())
                .build();
    }
}