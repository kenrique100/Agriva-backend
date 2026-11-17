package com.agriva.poultry.service.impl;

import com.agriva.poultry.mapper.PoultryMetricsMapper;
import com.agriva.poultry.model.dto.request.EggRecordRequest;
import com.agriva.poultry.model.dto.response.EggRecordResponse;
import com.agriva.poultry.model.entity.PoultryBatch;
import com.agriva.poultry.model.entity.PoultryEggRecord;
import com.agriva.poultry.model.enums.PoultryBatchStatus;
import com.agriva.poultry.model.enums.PoultrySpecies;
import com.agriva.poultry.repository.PoultryBatchRepository;
import com.agriva.poultry.repository.PoultryEggRecordRepository;
import com.agriva.poultry.service.PoultryEggRecordService;
import com.agriva.shared.exception.BusinessException;
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
public class PoultryEggRecordServiceImpl implements PoultryEggRecordService {

    private final PoultryEggRecordRepository eggRepo;
    private final PoultryBatchRepository batchRepo;
    private final PoultryMetricsMapper mapper;

    @Override
    @Transactional
    public EggRecordResponse record(UUID batchId, EggRecordRequest request,
                                    UUID farmId, UUID userId) {
        PoultryBatch batch = batchRepo.findByIdAndFarmId(batchId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Poultry batch not found with ID: " + batchId));

        if (batch.getStatus() != PoultryBatchStatus.ACTIVE) {
            throw new BusinessException(
                    "Cannot record egg production on a non-ACTIVE batch (status="
                            + batch.getStatus() + ")");
        }
        if (batch.getSpecies() != PoultrySpecies.LAYER
                && batch.getSpecies() != PoultrySpecies.DUCK) {
            throw new BusinessException(
                    "Egg production is only valid for LAYER or DUCK batches (species="
                            + batch.getSpecies() + ")");
        }
        if (request.getBrokenEggs() != null
                && request.getBrokenEggs() > request.getEggsCollected()) {
            throw new BusinessException(
                    "brokenEggs (" + request.getBrokenEggs()
                            + ") cannot exceed eggsCollected (" + request.getEggsCollected() + ")");
        }

        PoultryEggRecord saved = eggRepo.save(PoultryEggRecord.builder()
                .farmId(farmId)
                .batchId(batch.getId())
                .eggsCollected(request.getEggsCollected())
                .brokenEggs(request.getBrokenEggs() == null ? 0 : request.getBrokenEggs())
                .recordedAt(request.getRecordedAt())
                .recordedByUserId(userId)
                .build());

        log.info("Poultry egg record {} — batch {} collected={} broken={}",
                saved.getId(), batch.getId(),
                request.getEggsCollected(), request.getBrokenEggs());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EggRecordResponse> listForBatch(UUID batchId, UUID farmId, Pageable pageable) {
        batchRepo.findByIdAndFarmId(batchId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Poultry batch not found with ID: " + batchId));
        return eggRepo.findByBatchId(batchId, pageable).map(mapper::toResponse);
    }
}