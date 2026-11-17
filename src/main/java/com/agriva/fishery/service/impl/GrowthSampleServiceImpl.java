package com.agriva.fishery.service.impl;

import com.agriva.fishery.mapper.FisheryOpsMapper;
import com.agriva.fishery.model.dto.request.GrowthSampleRequest;
import com.agriva.fishery.model.dto.response.GrowthSampleResponse;
import com.agriva.fishery.model.entity.GrowthSample;
import com.agriva.fishery.model.entity.Pond;
import com.agriva.fishery.model.entity.PondStock;
import com.agriva.fishery.repository.GrowthSampleRepository;
import com.agriva.fishery.repository.PondRepository;
import com.agriva.fishery.repository.PondStockRepository;
import com.agriva.fishery.service.GrowthSampleService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.PondNotFoundException;
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
public class GrowthSampleServiceImpl implements GrowthSampleService {

    private final GrowthSampleRepository growthSampleRepository;
    private final PondRepository pondRepository;
    private final PondStockRepository stockRepository;
    private final FisheryOpsMapper mapper;

    @Override
    @Transactional
    public GrowthSampleResponse record(UUID pondId, GrowthSampleRequest request,
                                       UUID farmId, UUID userId) {
        Pond pond = pondRepository.findByIdAndFarmIdLive(pondId, farmId)
                .orElseThrow(() -> new PondNotFoundException("Pond not found with ID: " + pondId));

        PondStock stock = stockRepository
                .findByPondIdAndSpeciesForUpdate(pond.getId(), request.getSpecies())
                .orElseThrow(() -> new BusinessException(
                        "No stock for species '" + request.getSpecies() + "' in pond " + pondId));

        stock.setAverageWeightG(request.getAverageWeightG());
        stockRepository.save(stock);

        GrowthSample saved = growthSampleRepository.save(GrowthSample.builder()
                .farmId(farmId)
                .pondId(pond.getId())
                .species(request.getSpecies().trim())
                .averageWeightG(request.getAverageWeightG())
                .sampleSize(request.getSampleSize())
                .sampledAt(request.getSampledAt())
                .recordedByUserId(userId)
                .build());

        log.info("Growth sample {} recorded for pond {} ({} avg {} g, n={})",
                saved.getId(), pondId, request.getSpecies(),
                request.getAverageWeightG(), request.getSampleSize());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GrowthSampleResponse> listForPond(UUID pondId, UUID farmId, Pageable pageable) {
        pondRepository.findByIdAndFarmIdLive(pondId, farmId)
                .orElseThrow(() -> new PondNotFoundException("Pond not found with ID: " + pondId));
        return growthSampleRepository.findByPondId(pondId, pageable).map(mapper::toResponse);
    }
}