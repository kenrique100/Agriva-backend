package com.agriva.feeding.service;

import com.agriva.feeding.model.dto.response.*;
import com.agriva.shared.models.dto.BatchStatusResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

public interface FeedBulkService {

    FeedBulkUploadResponse bulkUploadIngredients(MultipartFile file, UUID farmId, UUID userId, String email);
    FeedBulkUploadResponse bulkUploadFormulations(MultipartFile file, UUID farmId, UUID userId, String email);
    FeedBulkUploadResponse bulkUploadProductions(MultipartFile file, UUID farmId, UUID userId, String email);
    FeedBulkUploadResponse bulkUploadFeedings(MultipartFile file, UUID farmId, UUID userId, String email);

    Page<StagedFeedingIngredientResponse> listStagedIngredients(UUID farmId, String status, Pageable pageable);
    Page<StagedFeedingFormulationResponse> listStagedFormulations(UUID farmId, String status, Pageable pageable);
    Page<StagedFeedingProductionResponse> listStagedProductions(UUID farmId, String status, Pageable pageable);
    Page<StagedFeedingEventResponse> listStagedFeedings(UUID farmId, String status, Pageable pageable);

    BatchStatusResponse bulkStatusIngredients(UUID batchId, UUID farmId);
    BatchStatusResponse bulkStatusFormulations(UUID batchId, UUID farmId);
    BatchStatusResponse bulkStatusProductions(UUID batchId, UUID farmId);
    BatchStatusResponse bulkStatusFeedings(UUID batchId, UUID farmId);

    Map<String, Long> ingredientsStats(UUID farmId);
    Map<String, Long> formulationsStats(UUID farmId);
    Map<String, Long> productionsStats(UUID farmId);
    Map<String, Long> feedingsStats(UUID farmId);
}