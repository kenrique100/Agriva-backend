package com.agriva.farming.services;

import com.agriva.farming.model.dto.requests.*;
import com.agriva.farming.model.dto.response.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CropManagementService {

    // --- Fields ---
    List<FieldResponse> getFields(UUID farmId);
    FieldResponse createField(FieldRequest req, UUID farmId);
    FieldResponse updateField(UUID id, FieldRequest req, UUID farmId);
    void deleteField(UUID id, UUID farmId);
    CropBulkUploadResponse bulkUploadFields(MultipartFile file, UUID farmId);

    // --- Crops ---
    List<CropResponse> getCrops(UUID farmId);
    CropResponse createCrop(CropRequest req, UUID farmId);
    CropResponse updateCrop(UUID id, CropRequest req, UUID farmId);
    void deleteCrop(UUID id, UUID farmId);
    CropBulkUploadResponse bulkUploadCrops(MultipartFile file, UUID farmId);

    // --- Cycles ---
    List<CropCycleResponse> getCycles(UUID farmId);
    CropCycleResponse getCycle(UUID id, UUID farmId);
    CropCycleResponse createCycle(CropCycleRequest req, UUID farmId);
    CropCycleResponse updateCycleStatus(UUID id, String status, UUID farmId);
    void deleteCycle(UUID id, UUID farmId);

    // --- Bulk / Staging ---
    CropBulkUploadResponse bulkUploadCycles(MultipartFile file, UUID farmId, UUID userId, String email);
    Page<StagedCropCycleResponse> listStaging(UUID farmId, String status, Pageable pageable);
    Map<String, Long> stagingStats(UUID farmId);

    // --- Activities ---
    List<CropActivityResponse> getActivities(UUID cycleId, UUID farmId);
    CropActivityResponse logActivity(UUID cycleId, CropActivityRequest req, UUID farmId, UUID userId);
    CropActivityResponse updateActivity(UUID cycleId, UUID activityId, CropActivityRequest req, UUID farmId); // NEW
    void deleteActivity(UUID cycleId, UUID activityId, UUID farmId);

    // --- Harvests ---
    List<HarvestResponse> getHarvests(UUID cycleId, UUID farmId);
    HarvestResponse logHarvest(UUID cycleId, HarvestRequest req, UUID farmId);
    HarvestResponse updateHarvest(UUID cycleId, UUID harvestId, HarvestRequest req, UUID farmId);
    void deleteHarvest(UUID cycleId, UUID harvestId, UUID farmId);
}