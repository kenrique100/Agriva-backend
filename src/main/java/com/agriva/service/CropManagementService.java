package com.agriva.service;

import com.agriva.model.dto.crop.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CropManagementService {

    // Fields
    List<FieldResponse> getFields(UUID farmId);
    FieldResponse createField(FieldRequest req, UUID farmId);
    CropBulkUploadResponse bulkUploadFields(MultipartFile file, UUID farmId);

    // Crops
    List<CropResponse> getCrops(UUID farmId);
    CropResponse createCrop(CropRequest req, UUID farmId);
    CropBulkUploadResponse bulkUploadCrops(MultipartFile file, UUID farmId);

    // Cycles
    List<CropCycleResponse> getCycles(UUID farmId);
    CropCycleResponse getCycle(UUID id, UUID farmId);
    CropCycleResponse createCycle(CropCycleRequest req, UUID farmId);
    CropCycleResponse updateCycleStatus(UUID id, String status, UUID farmId);
    void deleteCycle(UUID id, UUID farmId);

    // Bulk / staging
    CropBulkUploadResponse bulkUploadCycles(MultipartFile file, UUID farmId,
                                            UUID userId, String email);
    Page<StagedCropCycleResponse> listStaging(UUID farmId, String status, Pageable pageable);
    Map<String, Long> stagingStats(UUID farmId);

    // Activities
    List<CropActivityResponse> getActivities(UUID cycleId, UUID farmId);
    CropActivityResponse logActivity(UUID cycleId, CropActivityRequest req,
                                     UUID farmId, UUID userId);

    // Harvests
    HarvestResponse logHarvest(UUID cycleId, HarvestRequest req, UUID farmId);
}