package com.agriva.poultry.service.impl;

import com.agriva.poultry.mapper.PoultryBatchMapper;
import com.agriva.poultry.model.dto.request.PoultryBatchRequest;
import com.agriva.poultry.model.dto.response.PoultryBatchResponse;
import com.agriva.poultry.model.entity.PoultryBatch;
import com.agriva.poultry.model.entity.PoultryHouse;
import com.agriva.poultry.model.enums.PoultryBatchStatus;
import com.agriva.poultry.repository.PoultryBatchRepository;
import com.agriva.poultry.repository.PoultryHouseRepository;
import com.agriva.poultry.service.PoultryBatchCodeGenerator;
import com.agriva.poultry.service.PoultryBatchService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PoultryBatchServiceImpl implements PoultryBatchService {

    private static final int MAX_CODE_ATTEMPTS = 3;

    private final PoultryBatchRepository batchRepository;
    private final PoultryHouseRepository houseRepository;
    private final PoultryBatchMapper mapper;
    private final PoultryBatchCodeGenerator batchCodeGenerator;

    @Override
    @Transactional
    public PoultryBatchResponse createBatch(UUID houseId, PoultryBatchRequest req,
                                            UUID farmId, UUID userId) {

        PoultryHouse house = houseRepository.findByIdAndFarmIdLive(houseId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Poultry house not found with ID: " + houseId));

        DataIntegrityViolationException last = null;

        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            String code = batchCodeGenerator.nextCode(farmId);
            try {
                PoultryBatch batch = PoultryBatch.builder()
                        .farmId(farmId)
                        .houseId(house.getId())
                        .species(req.getSpecies())
                        .batchCode(code)
                        .initialCount(req.getInitialCount())
                        .currentCount(req.getInitialCount())
                        .arrivalDate(req.getArrivalDate())
                        .stage(req.getStage())
                        .averageWeightG(req.getAverageWeightG())
                        .initialAvgWeightG(req.getAverageWeightG())
                        .status(PoultryBatchStatus.ACTIVE)
                        .build();
                PoultryBatch saved = batchRepository.saveAndFlush(batch);

                log.info("PoultryBatch {} created for farm {} (house={}, code={}, by={})",
                        saved.getId(), farmId, house.getId(), saved.getBatchCode(), userId);

                return mapper.toResponse(saved, house.getName());
            } catch (DataIntegrityViolationException e) {
                last = e;
                log.warn("Batch code collision on attempt {}/{} (code={}): {}",
                        attempt, MAX_CODE_ATTEMPTS, code, e.getMessage());
            }
        }

        throw new BusinessException(
                "Could not allocate a unique batch code after " + MAX_CODE_ATTEMPTS
                        + " attempts"
                        + (last != null ? " — last error: " + last.getMessage() : ""));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PoultryBatchResponse> listBatchesByHouse(UUID houseId, UUID farmId) {
        PoultryHouse house = houseRepository.findByIdAndFarmIdLive(houseId, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Poultry house not found with ID: " + houseId));
        return batchRepository.findByHouseId(house.getId()).stream()
                .map(b -> mapper.toResponse(b, house.getName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PoultryBatchResponse> listBatchesByFarm(UUID farmId, Pageable pageable) {
        Page<PoultryBatch> page = batchRepository.findByFarmId(farmId, pageable);
        Map<UUID, String> houseNames = houseRepository
                .findByFarmIdLive(farmId, Pageable.unpaged())
                .getContent().stream()
                .collect(Collectors.toMap(PoultryHouse::getId, PoultryHouse::getName));
        return page.map(b -> mapper.toResponse(b, houseNames.get(b.getHouseId())));
    }

    @Override
    @Transactional(readOnly = true)
    public PoultryBatchResponse getBatch(UUID id, UUID farmId) {
        PoultryBatch batch = batchRepository.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Poultry batch not found with ID: " + id));
        String houseName = houseRepository.findById(batch.getHouseId())
                .map(PoultryHouse::getName).orElse(null);
        return mapper.toResponse(batch, houseName);
    }

    @Override
    @Transactional
    public PoultryBatchResponse updateStatus(UUID id, PoultryBatchStatus status, UUID farmId) {
        PoultryBatch batch = batchRepository.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Poultry batch not found with ID: " + id));

        batch.setStatus(status);
        PoultryBatch saved = batchRepository.save(batch);

        String houseName = houseRepository.findById(saved.getHouseId())
                .map(PoultryHouse::getName).orElse(null);
        log.info("Poultry batch {} status -> {} (farm={})", id, status, farmId);
        return mapper.toResponse(saved, houseName);
    }
}