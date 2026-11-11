package com.agriva.feeding.service.impl;

import com.agriva.feeding.mapper.FeedFeedingMapper;
import com.agriva.feeding.model.dto.request.FeedingRequest;
import com.agriva.feeding.model.dto.response.FeedingEventResponse;
import com.agriva.feeding.model.entity.FarmFeedStore;
import com.agriva.feeding.model.entity.FeedingEvent;
import com.agriva.feeding.port.ConsumerSnapshot;
import com.agriva.feeding.port.ConsumerType;
import com.agriva.feeding.port.FeedConsumerPort;
import com.agriva.feeding.repository.FarmFeedStoreRepository;
import com.agriva.feeding.repository.FeedBatchRepository;
import com.agriva.feeding.repository.FeedingEventRepository;
import com.agriva.feeding.service.FeedConsumptionService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.ConsumerNotFoundInDomainException;
import com.agriva.shared.exception.InsufficientFeedStoreException;
import com.agriva.shared.exception.ResourceNotFoundException;
import com.agriva.shared.exception.UnregisteredConsumerTypeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedConsumptionServiceImpl implements FeedConsumptionService {

    private final List<FeedConsumerPort> consumerPorts;
    private final FeedingEventRepository feedingEventRepository;
    private final FeedBatchRepository feedBatchRepository;
    private final FarmFeedStoreRepository farmStoreRepository;
    private final FeedFeedingMapper mapper;

    @Override
    @Transactional
    public FeedingEventResponse feed(FeedingRequest request, UUID farmId, UUID userId) {
        // 1. Resolve the port by consumer type
        FeedConsumerPort port = resolvePort(request.getConsumerType());

        // 2. Snapshot the consumer — 422 if the domain does not know it
        ConsumerSnapshot snapshot = port.snapshot(farmId, request.getConsumerId())
                .orElseThrow(() -> new ConsumerNotFoundInDomainException(
                        request.getConsumerType().name(), request.getConsumerId()));

        // 3. If a batch was referenced, it must exist on this farm (typo guard)
        if (request.getFeedBatchId() != null) {
            feedBatchRepository.findByIdAndFarmId(request.getFeedBatchId(), farmId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Feed batch not found with ID: " + request.getFeedBatchId()));
        }

        // 4. Lock the farm store, check balance — 422 if insufficient
        FarmFeedStore store = farmStoreRepository.findByFarmIdForUpdate(farmId)
                .orElseThrow(() -> new InsufficientFeedStoreException(
                        request.getQuantityKg(), BigDecimal.ZERO, "KG"));

        if (store.getQuantityOnHandKg().compareTo(request.getQuantityKg()) < 0) {
            throw new InsufficientFeedStoreException(
                    request.getQuantityKg(), store.getQuantityOnHandKg(), "KG");
        }

        // 5. Decrement the store
        BigDecimal newBalance = store.getQuantityOnHandKg()
                .subtract(request.getQuantityKg());
        store.setQuantityOnHandKg(newBalance);
        farmStoreRepository.save(store);

        // 6. Persist the immutable feeding event with the consumer snapshot
        FeedingEvent event = FeedingEvent.builder()
                .farmId(farmId)
                .consumerType(request.getConsumerType())
                .consumerId(request.getConsumerId())
                .consumerDisplayName(snapshot.displayName())
                .consumerHeadcount(snapshot.headcount())
                .consumerStage(snapshot.stage())
                .feedBatchId(request.getFeedBatchId())
                .quantityKg(request.getQuantityKg())
                .fedAt(request.getFedAt())
                .notes(request.getNotes())
                .createdByUserId(userId)
                .build();

        FeedingEvent saved = feedingEventRepository.save(event);

        log.info("Fed {} kg to {} {} ('{}', headcount={}); farm store now {} kg",
                request.getQuantityKg(), request.getConsumerType(),
                request.getConsumerId(), snapshot.displayName(),
                snapshot.headcount(), newBalance);

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FeedingEventResponse> listFeedings(UUID farmId,
                                                   ConsumerType consumerType,
                                                   UUID consumerId,
                                                   Pageable pageable) {
        if (consumerId != null && consumerType == null) {
            throw new BusinessException(
                    "consumerType is required when consumerId is provided");
        }
        Page<FeedingEvent> page;
        if (consumerType != null && consumerId != null) {
            page = feedingEventRepository.findByFarmIdAndConsumerTypeAndConsumerId(
                    farmId, consumerType, consumerId, pageable);
        } else if (consumerType != null) {
            page = feedingEventRepository.findByFarmIdAndConsumerType(
                    farmId, consumerType, pageable);
        } else {
            page = feedingEventRepository.findByFarmId(farmId, pageable);
        }
        return page.map(mapper::toResponse);
    }

    private FeedConsumerPort resolvePort(ConsumerType type) {
        return consumerPorts.stream()
                .filter(p -> p.consumerType() == type)
                .findFirst()
                .orElseThrow(() -> new UnregisteredConsumerTypeException(type.name()));
    }
}