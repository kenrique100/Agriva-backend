package com.agriva.feeding.service;

import com.agriva.feeding.model.dto.request.AdjustIngredientStockRequest;
import com.agriva.feeding.model.dto.request.IngredientRequest;
import com.agriva.feeding.model.dto.request.IngredientStockReplenishRequest;
import com.agriva.feeding.model.dto.request.WriteOffIngredientStockRequest;
import com.agriva.feeding.model.dto.response.IngredientResponse;
import com.agriva.feeding.model.dto.response.IngredientStatsResponse;
import com.agriva.feeding.model.dto.response.IngredientStockAdjustmentResponse;
import com.agriva.shared.models.dto.BatchStatusResponse;
import com.agriva.shared.models.dto.BatchUploadResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.UUID;

public interface IngredientService {

    IngredientResponse createIngredient(IngredientRequest request, UUID farmId, UUID userId);

    IngredientResponse createWithInitialStock(IngredientRequest request,
                                              BigDecimal initialQuantity,
                                              UUID farmId, UUID userId);

    Page<IngredientResponse> listIngredients(UUID farmId, Pageable pageable);

    IngredientResponse getIngredient(UUID id, UUID farmId);

    IngredientResponse updateIngredient(UUID id, IngredientRequest request, UUID farmId, UUID userId);

    IngredientResponse replenishStock(UUID id, IngredientStockReplenishRequest request,
                                      UUID farmId, UUID userId);

    void softDelete(UUID id, UUID farmId);

    BatchUploadResponse bulkUpload(MultipartFile file, UUID farmId, UUID userId, String email);

    BatchStatusResponse bulkStatus(UUID batchId, UUID farmId);

    IngredientStatsResponse getStats(UUID farmId);


    /** Absolute set. Records an {@code ABSOLUTE_SET} adjustment. */
    IngredientResponse adjustStock(UUID id, AdjustIngredientStockRequest request,
                                   UUID farmId, UUID userId);

    /** Deduct (≤ current). Records a {@code WRITEOFF} adjustment. */
    IngredientResponse writeOffStock(UUID id, WriteOffIngredientStockRequest request,
                                     UUID farmId, UUID userId);

    /** Paginated history of every adjustment recorded for this ingredient. */
    Page<IngredientStockAdjustmentResponse> listStockAdjustments(UUID id, UUID farmId, Pageable pageable);
}