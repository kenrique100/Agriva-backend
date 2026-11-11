package com.agriva.poultry.service;

import com.agriva.poultry.model.dto.request.PoultryHouseRequest;
import com.agriva.poultry.model.dto.response.PoultryHouseResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface PoultryHouseService {

    PoultryHouseResponse createHouse(PoultryHouseRequest request, UUID farmId, UUID userId);

    Page<PoultryHouseResponse> listHouses(UUID farmId, Pageable pageable);

    PoultryHouseResponse getHouse(UUID id, UUID farmId);

    PoultryHouseResponse updateHouse(UUID id, PoultryHouseRequest request, UUID farmId);

    void deleteHouse(UUID id, UUID farmId);
}