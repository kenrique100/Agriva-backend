package com.agriva.poultry.service.impl;

import com.agriva.poultry.mapper.PoultryHouseMapper;
import com.agriva.poultry.model.dto.request.PoultryHouseRequest;
import com.agriva.poultry.model.dto.response.PoultryHouseResponse;
import com.agriva.poultry.model.entity.PoultryBatch;
import com.agriva.poultry.model.entity.PoultryHouse;
import com.agriva.poultry.repository.PoultryBatchRepository;
import com.agriva.poultry.repository.PoultryHouseRepository;
import com.agriva.poultry.service.PoultryHouseService;
import com.agriva.shared.exception.BusinessException;
import com.agriva.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PoultryHouseServiceImpl implements PoultryHouseService {

    private final PoultryHouseRepository houseRepository;
    private final PoultryBatchRepository batchRepository;
    private final PoultryHouseMapper mapper;

    @Override
    @Transactional
    public PoultryHouseResponse createHouse(PoultryHouseRequest request, UUID farmId, UUID userId) {
        if (houseRepository.existsByFarmIdAndNameLive(farmId, request.getName())) {
            throw new BusinessException("Poultry house with this name already exists");
        }

        PoultryHouse house = PoultryHouse.builder()
                .farmId(farmId)
                .name(request.getName().trim())
                .location(request.getLocation())
                .capacity(request.getCapacity())
                .createdByUserId(userId)
                .active(true)
                .build();

        PoultryHouse saved = houseRepository.save(house);
        log.info("Poultry house {} created for farm {} (name='{}')",
                saved.getId(), farmId, saved.getName());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PoultryHouseResponse> listHouses(UUID farmId, Pageable pageable) {
        return houseRepository.findByFarmIdLive(farmId, pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PoultryHouseResponse getHouse(UUID id, UUID farmId) {
        PoultryHouse house = loadHouse(id, farmId);
        List<PoultryBatch> batches = batchRepository.findByHouseId(id);
        return mapper.toResponse(house, mapper.toBatchSummary(batches));
    }

    @Override
    @Transactional
    public PoultryHouseResponse updateHouse(UUID id, PoultryHouseRequest request, UUID farmId) {
        PoultryHouse house = loadHouse(id, farmId);

        if (!house.getName().equals(request.getName())
                && houseRepository.existsByFarmIdAndNameLiveExcluding(farmId, request.getName(), id)) {
            throw new BusinessException("Poultry house with this name already exists");
        }

        house.setName(request.getName().trim());
        house.setLocation(request.getLocation());
        house.setCapacity(request.getCapacity());

        PoultryHouse saved = houseRepository.save(house);
        log.info("Poultry house {} updated for farm {}", id, farmId);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteHouse(UUID id, UUID farmId) {
        PoultryHouse house = loadHouse(id, farmId);
        house.setDeletedAt(LocalDateTime.now());
        house.setActive(false);
        houseRepository.save(house);
        log.info("Poultry house {} soft-deleted from farm {}", id, farmId);
    }

    private PoultryHouse loadHouse(UUID id, UUID farmId) {
        return houseRepository.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Poultry house not found with ID: " + id));
    }
}