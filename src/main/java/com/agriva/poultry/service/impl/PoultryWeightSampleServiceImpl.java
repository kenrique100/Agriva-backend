package com.agriva.poultry.service.impl;

import com.agriva.poultry.mapper.PoultryMetricsMapper;
import com.agriva.poultry.model.dto.request.WeightSampleRequest;
import com.agriva.poultry.model.dto.response.WeightSampleResponse;
import com.agriva.poultry.model.entity.PoultryBatch;
import com.agriva.poultry.model.entity.PoultryWeightSample;
import com.agriva.poultry.model.enums.PoultryBatchStatus;
import com.agriva.poultry.repository.PoultryBatchRepository;
import com.agriva.poultry.repository.PoultryWeightSampleRepository;
import com.agriva.poultry.service.PoultryWeightSampleService;
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
public class PoultryWeightSampleServiceImpl implements PoultryWeightSampleService {

    private final PoultryWeightSampleRepository sampleRepo;
    private final PoultryBatchRepository batchRepo;
    private final PoultryMetricsMapper mapper;

    @Override
    @Transactional
    public WeightSampleResponse record(UUID batchId, WeightSampleRequest request,
                                       UUID farmId, UUID userId) {
        PoultryBatch batch = batchRepo.findByIdAndFarmIdForUpdate(batchId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Poultry batch not found with ID: " + batchId));

        if (batch.getStatus() != PoultryBatchStatus.ACTIVE) {
            throw new BusinessException(
                    "Cannot record weight sample on a non-ACTIVE batch (status="
                            + batch.getStatus() + ")");
        }

        // First sample seeds the baseline used by FcrService for weight-gain math.
        if (batch.getInitialAvgWeightG() == null) {
            batch.setInitialAvgWeightG(request.getAverageWeightG());
        }
        batch.setAverageWeightG(request.getAverageWeightG());
        batchRepo.save(batch);

        PoultryWeightSample saved = sampleRepo.save(PoultryWeightSample.builder()
                .farmId(farmId)
                .batchId(batch.getId())
                .averageWeightG(request.getAverageWeightG())
                .sampleSize(request.getSampleSize())
                .sampledAt(request.getSampledAt())
                .recordedByUserId(userId)
                .build());

        log.info("Poultry weight sample {} recorded — batch {} avg {}g (n={})",
                saved.getId(), batch.getId(), request.getAverageWeightG(), request.getSampleSize());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WeightSampleResponse> listForBatch(UUID batchId, UUID farmId, Pageable pageable) {
        batchRepo.findByIdAndFarmId(batchId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Poultry batch not found with ID: " + batchId));
        return sampleRepo.findByBatchId(batchId, pageable).map(mapper::toResponse);
    }
}