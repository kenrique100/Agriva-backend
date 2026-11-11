package com.agriva.fishery.service.impl;

import com.agriva.fishery.mapper.FisheryOpsMapper;
import com.agriva.fishery.model.dto.request.MortalityRequest;
import com.agriva.fishery.model.dto.response.MortalityResponse;
import com.agriva.fishery.model.entity.MortalityRecord;
import com.agriva.fishery.model.entity.Pond;
import com.agriva.fishery.model.entity.PondStock;
import com.agriva.fishery.repository.MortalityRecordRepository;
import com.agriva.fishery.repository.PondRepository;
import com.agriva.fishery.repository.PondStockRepository;
import com.agriva.fishery.service.MortalityService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.InsufficientPondStockException;
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
public class MortalityServiceImpl implements MortalityService {

    private final MortalityRecordRepository mortalityRepository;
    private final PondRepository pondRepository;
    private final PondStockRepository stockRepository;
    private final FisheryOpsMapper mapper;

    @Override
    @Transactional
    public MortalityResponse record(UUID pondId, MortalityRequest request,
                                    UUID farmId, UUID userId) {
        Pond pond = pondRepository.findByIdAndFarmIdLive(pondId, farmId)
                .orElseThrow(() -> new PondNotFoundException("Pond not found with ID: " + pondId));

        // Lock the matching (pond, species) stock row — deterministic, so
        // concurrent mortalities on the same pond + species serialize.
        PondStock stock = stockRepository
                .findByPondIdAndSpeciesForUpdate(pond.getId(), request.getSpecies())
                .orElseThrow(() -> new BusinessException(
                        "No stock for species '" + request.getSpecies() + "' in pond " + pondId));

        long current = stock.getQuantity() == null ? 0L : stock.getQuantity();
        if (current < request.getQuantity()) {
            throw new InsufficientPondStockException(
                    request.getSpecies(), request.getQuantity(), current);
        }
        stock.setQuantity(current - request.getQuantity());
        stockRepository.save(stock);

        MortalityRecord saved = mortalityRepository.save(MortalityRecord.builder()
                .farmId(farmId)
                .pondId(pond.getId())
                .species(request.getSpecies().trim())
                .quantity(request.getQuantity())
                .recordedAt(request.getRecordedAt())
                .suspectedCause(request.getSuspectedCause())
                .recordedByUserId(userId)
                .build());

        log.info("Mortality {} recorded for pond {} — {} {} died (stock {} -> {})",
                saved.getId(), pondId, request.getQuantity(),
                request.getSpecies(), current, stock.getQuantity());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MortalityResponse> listForPond(UUID pondId, UUID farmId, Pageable pageable) {
        pondRepository.findByIdAndFarmIdLive(pondId, farmId)
                .orElseThrow(() -> new PondNotFoundException("Pond not found with ID: " + pondId));
        return mortalityRepository.findByPondId(pondId, pageable).map(mapper::toResponse);
    }
}