package com.agriva.poultry.service.impl;

import com.agriva.poultry.mapper.PoultryMetricsMapper;
import com.agriva.poultry.model.dto.request.MortalityRecordRequest;
import com.agriva.poultry.model.dto.response.MortalityRecordResponse;
import com.agriva.poultry.model.entity.PoultryBatch;
import com.agriva.poultry.model.entity.PoultryMortalityRecord;
import com.agriva.poultry.model.enums.PoultryBatchStatus;
import com.agriva.poultry.repository.PoultryBatchRepository;
import com.agriva.poultry.repository.PoultryMortalityRecordRepository;
import com.agriva.poultry.service.PoultryMortalityService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.InsufficientPoultryStockException;
import com.agriva.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PoultryMortalityServiceImpl implements PoultryMortalityService {

    private final PoultryMortalityRecordRepository mortalityRepo;
    private final PoultryBatchRepository batchRepo;
    private final PoultryMetricsMapper mapper;

    @Override
    @Transactional
    public MortalityRecordResponse record(UUID batchId, MortalityRecordRequest request,
                                          UUID farmId, UUID userId) {
        PoultryBatch batch = batchRepo.findByIdAndFarmIdForUpdate(batchId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Poultry batch not found with ID: " + batchId));

        if (batch.getStatus() != PoultryBatchStatus.ACTIVE) {
            throw new BusinessException(
                    "Cannot record mortality on a non-ACTIVE batch (status=" + batch.getStatus() + ")");
        }

        long current = batch.getCurrentCount() == null ? 0L : batch.getCurrentCount();
        if (current < request.getQuantity()) {
            throw new InsufficientPoultryStockException(
                    batch.getBatchCode() != null ? batch.getBatchCode() : batch.getId().toString(),
                    request.getQuantity(), current);
        }

        batch.setCurrentCount(current - request.getQuantity());
        batchRepo.save(batch);

        PoultryMortalityRecord saved = mortalityRepo.save(PoultryMortalityRecord.builder()
                .farmId(farmId)
                .batchId(batch.getId())
                .quantity(request.getQuantity())
                .recordedAt(request.getRecordedAt())
                .suspectedCause(request.getSuspectedCause())
                .recordedByUserId(userId)
                .build());

        log.info("Poultry mortality {} recorded — batch {} lost {} birds ({} -> {})",
                saved.getId(), batch.getId(), request.getQuantity(),
                current, batch.getCurrentCount());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MortalityRecordResponse> listForBatch(UUID batchId, UUID farmId, Pageable pageable) {
        batchRepo.findByIdAndFarmId(batchId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Poultry batch not found with ID: " + batchId));
        return mortalityRepo.findByBatchId(batchId, pageable).map(mapper::toResponse);
    }
}