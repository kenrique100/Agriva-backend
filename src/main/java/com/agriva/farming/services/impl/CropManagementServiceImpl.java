package com.agriva.farming.services.impl;

import com.agriva.farming.kafka.producer.CropCycleProducer;
import com.agriva.farming.mapper.CropMapper;
import com.agriva.farming.model.dto.requests.*;
import com.agriva.farming.model.dto.response.*;
import com.agriva.farming.model.entity.*;
import com.agriva.farming.model.enums.CropCycleStatus;
import com.agriva.farming.model.enums.StagedCropCycleStatus;
import com.agriva.farming.repository.*;
import com.agriva.farming.services.CropManagementService;
import com.agriva.farming.utils.CropCycleRules;
import com.agriva.farming.utils.CropCycleExcelParser;
import com.agriva.farming.utils.CropExcelParser;
import com.agriva.farming.utils.FieldExcelParser;
import com.agriva.shared.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CropManagementServiceImpl implements CropManagementService {

    private final FieldRepository fieldRepo;
    private final CropRepository cropRepo;
    private final CropCycleRepository cycleRepo;
    private final CropActivityRepository activityRepo;
    private final HarvestRepository harvestRepo;
    private final StagedCropCycleRepository stagedRepo;
    private final CropMapper mapper;

    private final FieldExcelParser fieldExcelParser;
    private final CropExcelParser cropExcelParser;
    private final CropCycleExcelParser cropCycleExcelParser;

    private final CropCycleProducer cropCycleProducer;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<FieldResponse> getFields(UUID farmId) {
        return fieldRepo.findByFarmId(farmId).stream()
                .map(mapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public FieldResponse createField(FieldRequest req, UUID farmId) {
        if (fieldRepo.existsByFarmIdAndName(farmId, req.getName())) {
            throw new BusinessException("Field with this name already exists");
        }
        Field f = Field.builder()
                .farmId(farmId).name(req.getName())
                .sizeHectares(req.getSizeHectares()).soilType(req.getSoilType())
                .build();
        return mapper.toResponse(fieldRepo.save(f));
    }

    @Override
    @Transactional
    public FieldResponse updateField(UUID id, FieldRequest req, UUID farmId) {
        Field field = fieldRepo.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new FieldNotFoundException("Field not found"));
        if (!field.getName().equals(req.getName())
                && fieldRepo.existsByFarmIdAndName(farmId, req.getName())) {
            throw new BusinessException("Field with this name already exists");
        }
        field.setName(req.getName());
        field.setSizeHectares(req.getSizeHectares());
        field.setSoilType(req.getSoilType());
        return mapper.toResponse(fieldRepo.save(field));
    }

    @Override
    @Transactional
    public void deleteField(UUID id, UUID farmId) {
        Field field = fieldRepo.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new FieldNotFoundException("Field not found"));
        fieldRepo.delete(field);
        log.info("Deleted field {} from farm {}", id, farmId);
    }

    @Override
    @Transactional
    public CropBulkUploadResponse bulkUploadFields(MultipartFile file, UUID farmId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required");
        }
        List<FieldExcelParser.ParsedRow> rows;
        try { rows = fieldExcelParser.parse(file.getInputStream()); }
        catch (Exception e) {
            throw new IllegalArgumentException("Failed to read Excel file: " + e.getMessage(), e);
        }
        CropBulkUploadResponse response = new CropBulkUploadResponse();
        response.setTotalRows(rows.size());
        for (FieldExcelParser.ParsedRow row : rows) {
            if (row.isError()) {
                response.setFailed(response.getFailed() + 1);
                response.getResults().add(new CropBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", row.error()));
                continue;
            }
            try {
                FieldResponse created = createField(row.request(), farmId);
                response.setCreated(response.getCreated() + 1);
                response.getResults().add(new CropBulkUploadResponse.RowResult(
                        row.rowNumber(), created.getId().toString(), "CREATED", null));
            } catch (Exception e) {
                response.setFailed(response.getFailed() + 1);
                response.getResults().add(new CropBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", e.getMessage()));
            }
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CropResponse> getCrops(UUID farmId) {
        return cropRepo.findByFarmId(farmId).stream()
                .map(mapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CropResponse createCrop(CropRequest req, UUID farmId) {
        Crop c = Crop.builder()
                .farmId(farmId).name(req.getName())
                .variety(req.getVariety())
                .expectedDaysToHarvest(req.getExpectedDaysToHarvest())
                .build();
        return mapper.toResponse(cropRepo.save(c));
    }

    @Override
    @Transactional
    public CropResponse updateCrop(UUID id, CropRequest req, UUID farmId) {
        Crop crop = cropRepo.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new CropNotFoundException("Crop not found"));
        crop.setName(req.getName());
        crop.setVariety(req.getVariety());
        crop.setExpectedDaysToHarvest(req.getExpectedDaysToHarvest());
        return mapper.toResponse(cropRepo.save(crop));
    }

    @Override
    @Transactional
    public void deleteCrop(UUID id, UUID farmId) {
        Crop crop = cropRepo.findByIdAndFarmId(id, farmId)
                .orElseThrow(() -> new CropNotFoundException("Crop not found"));
        cropRepo.delete(crop);
    }

    @Override
    @Transactional
    public CropBulkUploadResponse bulkUploadCrops(MultipartFile file, UUID farmId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required");
        }
        List<CropExcelParser.ParsedRow> rows;
        try { rows = cropExcelParser.parse(file.getInputStream()); }
        catch (Exception e) {
            throw new IllegalArgumentException("Failed to read Excel file: " + e.getMessage(), e);
        }
        CropBulkUploadResponse response = new CropBulkUploadResponse();
        response.setTotalRows(rows.size());
        for (CropExcelParser.ParsedRow row : rows) {
            if (row.isError()) {
                response.setFailed(response.getFailed() + 1);
                response.getResults().add(new CropBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", row.error()));
                continue;
            }
            try {
                CropResponse created = createCrop(row.request(), farmId);
                response.setCreated(response.getCreated() + 1);
                response.getResults().add(new CropBulkUploadResponse.RowResult(
                        row.rowNumber(), created.getId().toString(), "CREATED", null));
            } catch (Exception e) {
                response.setFailed(response.getFailed() + 1);
                response.getResults().add(new CropBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", e.getMessage()));
            }
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CropCycleResponse> getCycles(UUID farmId) {
        return cycleRepo.findByFarmIdLive(farmId).stream()
                .map(mapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CropCycleResponse getCycle(UUID id, UUID farmId) {
        return mapper.toResponse(loadCycle(id, farmId));
    }

    @Override
    @Transactional
    public CropCycleResponse createCycle(CropCycleRequest req, UUID farmId) {
        Field field = fieldRepo.findByIdAndFarmId(req.getFieldId(), farmId)
                .orElseThrow(() -> new FieldNotFoundException("Field not found"));
        Crop crop = cropRepo.findByIdAndFarmId(req.getCropId(), farmId)
                .orElseThrow(() -> new CropNotFoundException("Crop not found"));

        CropCycle cycle = CropCycle.builder()
                .farmId(farmId).field(field).crop(crop)
                .plantingDate(req.getPlantingDate())
                .expectedHarvestDate(CropCycleRules.expectedHarvestFor(crop, req.getPlantingDate()))
                .status(CropCycleStatus.PLANTED)
                .notes(req.getNotes())
                .build();
        return mapper.toResponse(cycleRepo.save(cycle));
    }

    @Override
    @Transactional
    public CropCycleResponse updateCycleStatus(UUID id, String status, UUID farmId) {
        CropCycle cycle = loadCycle(id, farmId);

        CropCycleStatus target;
        try { target = CropCycleStatus.valueOf(status.toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid cycle status: " + status);
        }

        CropCycleRules.requireAllowed(cycle.getStatus(), target);

        cycle.setStatus(target);
        if (target == CropCycleStatus.HARVESTED && cycle.getActualHarvestDate() == null) {
            cycle.setActualHarvestDate(LocalDate.now());
        }
        return mapper.toResponse(cycleRepo.save(cycle));
    }

    @Override
    @Transactional
    public void deleteCycle(UUID id, UUID farmId) {
        CropCycle cycle = loadCycle(id, farmId);
        cycle.setDeletedAt(LocalDateTime.now());
        cycleRepo.save(cycle);
        log.info("Soft-deleted crop cycle {} from farm {}", id, farmId);
    }

    // ---------- Bulk / Staging ----------

    @Override
    public CropBulkUploadResponse bulkUploadCycles(MultipartFile file, UUID farmId,
                                                   UUID userId, String email) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required");
        }
        List<CropCycleExcelParser.ParsedRow> rows;
        try { rows = cropCycleExcelParser.parse(file.getInputStream(), farmId); }
        catch (Exception e) {
            throw new IllegalArgumentException("Failed to read Excel file: " + e.getMessage(), e);
        }

        CropBulkUploadResponse response = new CropBulkUploadResponse();
        response.setTotalRows(rows.size());

        for (CropCycleExcelParser.ParsedRow row : rows) {
            if (row.isError()) {
                response.setFailed(response.getFailed() + 1);
                response.getResults().add(new CropBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", row.error()));
                continue;
            }
            UUID submissionId = UUID.randomUUID();
            try {
                String payload = objectMapper.writeValueAsString(row.request());
                cropCycleProducer.publish(new CropCycleSubmissionEvent(
                        submissionId, farmId, userId, email, payload, LocalDateTime.now()));
                response.setQueued(response.getQueued() + 1);
                response.getResults().add(new CropBulkUploadResponse.RowResult(
                        row.rowNumber(), submissionId.toString(), "QUEUED", null));
            } catch (Exception e) {
                response.setFailed(response.getFailed() + 1);
                response.getResults().add(new CropBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", e.getMessage()));
            }
        }
        log.info("Bulk crop cycle upload for farm {}: total={}, queued={}, failed={}",
                farmId, response.getTotalRows(), response.getQueued(), response.getFailed());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StagedCropCycleResponse> listStaging(UUID farmId, String status, Pageable pageable) {
        Page<StagedCropCycle> page;
        if (status != null && !status.isBlank()) {
            page = stagedRepo.findByFarmIdAndStatus(
                    farmId, StagedCropCycleStatus.valueOf(status.toUpperCase()), pageable);
        } else {
            page = stagedRepo.findByFarmId(farmId, pageable);
        }
        return page.map(s -> new StagedCropCycleResponse(
                s.getId(), s.getFarmId(), s.getStatus().name(),
                s.getValidationErrors(), s.getPayloadJson(),
                s.getSubmittedByUserId(), s.getSubmittedByEmail(),
                s.getSubmittedAt(), s.getLastAttemptAt(), s.getNextRetryAt(),
                s.getProcessedAt(), s.getMovedCycleId(), s.getRetryCount()));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> stagingStats(UUID farmId) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (StagedCropCycleStatus s : StagedCropCycleStatus.values()) {
            out.put(s.name(), stagedRepo.countByFarmIdAndStatus(farmId, s));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CropActivityResponse> getActivities(UUID cycleId, UUID farmId) {
        loadCycle(cycleId, farmId);
        return activityRepo.findByCropCycleIdOrderByActivityDateDesc(cycleId).stream()
                .map(mapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CropActivityResponse logActivity(UUID cycleId, CropActivityRequest req,
                                            UUID farmId, UUID userId) {
        CropCycle cycle = loadCycle(cycleId, farmId);
        CropActivity activity = CropActivity.builder()
                .farmId(farmId).cropCycle(cycle).loggedByUserId(userId)
                .activityType(req.getActivityType()).activityDate(req.getActivityDate())
                .notes(req.getNotes()).linkedExpenseId(req.getLinkedExpenseId())
                .linkedTaskId(req.getLinkedTaskId())
                .build();
        return mapper.toResponse(activityRepo.save(activity));
    }

    @Override
    @Transactional
    public CropActivityResponse updateActivity(UUID cycleId, UUID activityId,
                                               CropActivityRequest req, UUID farmId) {
        loadCycle(cycleId, farmId);
        CropActivity activity = activityRepo.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Activity not found"));
        if (!activity.getFarmId().equals(farmId)
                || !activity.getCropCycle().getId().equals(cycleId)) {
            throw new ResourceNotFoundException("Activity not found for this cycle");
        }
        activity.setActivityType(req.getActivityType());
        activity.setActivityDate(req.getActivityDate());
        activity.setNotes(req.getNotes());
        activity.setLinkedExpenseId(req.getLinkedExpenseId());
        activity.setLinkedTaskId(req.getLinkedTaskId());
        return mapper.toResponse(activityRepo.save(activity));
    }

    @Override
    @Transactional
    public void deleteActivity(UUID cycleId, UUID activityId, UUID farmId) {
        loadCycle(cycleId, farmId);
        CropActivity activity = activityRepo.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Activity not found"));
        if (!activity.getFarmId().equals(farmId)
                || !activity.getCropCycle().getId().equals(cycleId)) {
            throw new ResourceNotFoundException("Activity not found for this cycle");
        }
        activityRepo.delete(activity);
    }

    // ---------- Harvests ----------

    @Override
    @Transactional(readOnly = true)
    public List<HarvestResponse> getHarvests(UUID cycleId, UUID farmId) {
        loadCycle(cycleId, farmId);
        return harvestRepo.findByCropCycleId(cycleId).stream()
                .map(mapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public HarvestResponse logHarvest(UUID cycleId, HarvestRequest req, UUID farmId) {
        CropCycle cycle = loadCycle(cycleId, farmId);
        if (cycle.getStatus() == CropCycleStatus.HARVESTED) {
            throw new BusinessException(
                    "Cycle already harvested — reopen the cycle before logging another harvest");
        }
        Harvest harvest = Harvest.builder()
                .farmId(farmId).cropCycle(cycle)
                .harvestDate(req.getHarvestDate()).quantity(req.getQuantity())
                .unit(req.getUnit()).qualityGrade(req.getQualityGrade())
                .linkedIncomeId(req.getLinkedIncomeId())
                .build();
        cycle.setStatus(CropCycleStatus.HARVESTED);
        cycle.setActualHarvestDate(req.getHarvestDate());
        cycleRepo.save(cycle);
        return mapper.toResponse(harvestRepo.save(harvest));
    }

    @Override
    @Transactional
    public HarvestResponse updateHarvest(UUID cycleId, UUID harvestId,
                                         HarvestRequest req, UUID farmId) {
        loadCycle(cycleId, farmId);
        Harvest harvest = harvestRepo.findById(harvestId)
                .orElseThrow(() -> new ResourceNotFoundException("Harvest not found"));
        if (!harvest.getFarmId().equals(farmId)
                || !harvest.getCropCycle().getId().equals(cycleId)) {
            throw new ResourceNotFoundException("Harvest not found for this cycle");
        }
        harvest.setHarvestDate(req.getHarvestDate());
        harvest.setQuantity(req.getQuantity());
        harvest.setUnit(req.getUnit());
        harvest.setQualityGrade(req.getQualityGrade());
        harvest.setLinkedIncomeId(req.getLinkedIncomeId());
        return mapper.toResponse(harvestRepo.save(harvest));
    }

    @Override
    @Transactional
    public void deleteHarvest(UUID cycleId, UUID harvestId, UUID farmId) {
        loadCycle(cycleId, farmId);
        Harvest harvest = harvestRepo.findById(harvestId)
                .orElseThrow(() -> new ResourceNotFoundException("Harvest not found"));
        if (!harvest.getFarmId().equals(farmId)
                || !harvest.getCropCycle().getId().equals(cycleId)) {
            throw new ResourceNotFoundException("Harvest not found for this cycle");
        }
        harvestRepo.delete(harvest);
    }

    private CropCycle loadCycle(UUID id, UUID farmId) {
        return cycleRepo.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new CropCycleNotFoundException("Crop cycle not found"));
    }
}