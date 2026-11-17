package com.agriva.feeding.service;

import com.agriva.feeding.model.dto.request.IngredientRequest;
import com.agriva.feeding.model.dto.request.IngredientStockReplenishRequest;
import com.agriva.feeding.model.dto.response.IngredientResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface IngredientService {

    IngredientResponse createIngredient(IngredientRequest request, UUID farmId, UUID userId);

    Page<IngredientResponse> listIngredients(UUID farmId, Pageable pageable);

    IngredientResponse getIngredient(UUID id, UUID farmId);

    IngredientResponse replenishStock(UUID id, IngredientStockReplenishRequest request,
                                      UUID farmId, UUID userId);
}