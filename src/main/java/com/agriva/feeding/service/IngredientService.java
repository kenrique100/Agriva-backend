package com.agriva.feeding.service;

import com.agriva.feeding.model.dto.request.IngredientRequest;
import com.agriva.feeding.model.dto.request.IngredientStockReplenishRequest;
import com.agriva.feeding.model.dto.response.IngredientResponse;
import com.agriva.shared.models.dto.BatchStatusResponse;
import com.agriva.shared.models.dto.BatchUploadResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface IngredientService {

    IngredientResponse createIngredient(IngredientRequest request, UUID farmId, UUID userId);

    Page<IngredientResponse> listIngredients(UUID farmId, Pageable pageable);

    IngredientResponse getIngredient(UUID id, UUID farmId);

    IngredientResponse replenishStock(UUID id, IngredientStockReplenishRequest request,
                                      UUID farmId, UUID userId);

    void softDelete(UUID id, UUID farmId);

    BatchUploadResponse bulkUpload(MultipartFile file, UUID farmId, UUID userId, String email);

    BatchStatusResponse bulkStatus(UUID batchId, UUID farmId);
}