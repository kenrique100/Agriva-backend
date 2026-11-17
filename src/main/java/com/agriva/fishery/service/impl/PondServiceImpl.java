package com.agriva.fishery.service.impl;

import com.agriva.fishery.mapper.PondMapper;
import com.agriva.fishery.model.dto.request.PondRequest;
import com.agriva.fishery.model.dto.request.PondStockRequest;
import com.agriva.fishery.model.dto.response.PondResponse;
import com.agriva.fishery.model.dto.response.PondStockResponse;
import com.agriva.fishery.model.entity.Pond;
import com.agriva.fishery.model.entity.PondStock;
import com.agriva.fishery.repository.PondRepository;
import com.agriva.fishery.repository.PondStockRepository;
import com.agriva.fishery.service.PondService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.PondNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PondServiceImpl implements PondService {

    private final PondRepository pondRepository;
    private final PondStockRepository pondStockRepository;
    private final PondMapper mapper;

    @Override
    @Transactional
    public PondResponse createPond(PondRequest request, UUID farmId, UUID userId) {
        if (pondRepository.existsByFarmIdAndNameLive(farmId, request.getName())) {
            throw new BusinessException("Pond with this name already exists");
        }

        Pond pond = Pond.builder()
                .farmId(farmId)
                .name(request.getName().trim())
                .location(request.getLocation())
                .surfaceAreaM2(request.getSurfaceAreaM2())
                .depthM(request.getDepthM())
                .createdByUserId(userId)
                .active(true)
                .build();

        Pond saved = pondRepository.save(pond);
        log.info("Pond {} created for farm {} (name='{}')", saved.getId(), farmId, saved.getName());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PondResponse> listPonds(UUID farmId, Pageable pageable) {
        return pondRepository.findByFarmIdLive(farmId, pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PondResponse getPond(UUID id, UUID farmId) {
        Pond pond = loadPond(id, farmId);
        List<PondStock> stocks = pondStockRepository.findByPondId(id);
        return mapper.toResponse(pond, mapper.toStockSummary(stocks));
    }

    @Override
    @Transactional
    public PondResponse updatePond(UUID id, PondRequest request, UUID farmId) {
        Pond pond = loadPond(id, farmId);

        if (!pond.getName().equals(request.getName())
                && pondRepository.existsByFarmIdAndNameLiveExcluding(farmId, request.getName(), id)) {
            throw new BusinessException("Pond with this name already exists");
        }

        pond.setName(request.getName().trim());
        pond.setLocation(request.getLocation());
        pond.setSurfaceAreaM2(request.getSurfaceAreaM2());
        pond.setDepthM(request.getDepthM());

        Pond saved = pondRepository.save(pond);
        log.info("Pond {} updated for farm {}", id, farmId);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deletePond(UUID id, UUID farmId) {
        Pond pond = loadPond(id, farmId);
        pond.setDeletedAt(LocalDateTime.now());
        pond.setActive(false);
        pondRepository.save(pond);
        log.info("Pond {} soft-deleted from farm {}", id, farmId);
    }

    @Override
    @Transactional
    public PondStockResponse addStock(UUID pondId, PondStockRequest request, UUID farmId) {
        Pond pond = loadPond(pondId, farmId);

        PondStock stock = PondStock.builder()
                .pondId(pond.getId())
                .species(request.getSpecies().trim())
                .quantity(request.getQuantity())
                .averageWeightG(request.getAverageWeightG())
                .stage(request.getStage())
                .stockedAt(request.getStockedAt() != null ? request.getStockedAt() : LocalDate.now())
                .build();

        PondStock saved = pondStockRepository.save(stock);
        log.info("Added {} {} to pond {} (farm {})",
                request.getQuantity(), request.getSpecies(), pondId, farmId);
        return mapper.toStockResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PondStockResponse> getStock(UUID pondId, UUID farmId) {
        loadPond(pondId, farmId); // access check
        return pondStockRepository.findByPondId(pondId).stream()
                .map(mapper::toStockResponse)
                .collect(Collectors.toList());
    }

    private Pond loadPond(UUID id, UUID farmId) {
        return pondRepository.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new PondNotFoundException("Pond not found with ID: " + id));
    }
}