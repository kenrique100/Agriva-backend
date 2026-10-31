package com.agriva.service.impl;

import com.agriva.exception.BusinessException;
import com.agriva.exception.ResourceNotFoundException;
import com.agriva.kafka.producer.CropCycleProducer;
import com.agriva.mapper.CropMapper;
import com.agriva.model.dto.crop.*;
import com.agriva.model.entity.*;
import com.agriva.model.entity.enums.CropCycleStatus;
import com.agriva.model.entity.enums.StagedCropCycleStatus;
import com.agriva.repository.*;
import com.agriva.service.CropManagementService;
import com.agriva.utils.CropCycleExcelParser;
import com.agriva.utils.CropExcelParser;
import com.agriva.utils.FieldExcelParser;
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
                .orElseThrow(() -> new ResourceNotFoundException("Field not found"));
        Crop crop = cropRepo.findByIdAndFarmId(req.getCropId(), farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Crop not found"));

        LocalDate expectedHarvest = req.getPlantingDate();
        if (crop.getExpectedDaysToHarvest() != null) {
            expectedHarvest = expectedHarvest.plusDays(crop.getExpectedDaysToHarvest());
        }

        CropCycle cycle = CropCycle.builder()
                .farmId(farmId).field(field).crop(crop)
                .plantingDate(req.getPlantingDate())
                .expectedHarvestDate(expectedHarvest)
                .status(CropCycleStatus.PLANTED)
                .notes(req.getNotes())
                .build();
        return mapper.toResponse(cycleRepo.save(cycle));
    }

    @Override
    @Transactional
    public CropCycleResponse updateCycleStatus(UUID id, String status, UUID farmId) {
        CropCycle cycle = loadCycle(id, farmId);
        try { cycle.setStatus(CropCycleStatus.valueOf(status.toUpperCase())); }
        catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid cycle status: " + status);
        }
        if (cycle.getStatus() == CropCycleStatus.HARVESTED) {
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
                response.setCreated(response.getCreated() + 1);
                response.getResults().add(new CropBulkUploadResponse.RowResult(
                        row.rowNumber(), submissionId.toString(), "QUEUED", null));
            } catch (Exception e) {
                response.setFailed(response.getFailed() + 1);
                response.getResults().add(new CropBulkUploadResponse.RowResult(
                        row.rowNumber(), null, "FAILED", e.getMessage()));
            }
        }

        log.info("Bulk crop cycle upload for farm {}: total={}, queued={}, failed={}",
                farmId, response.getTotalRows(), response.getCreated(), response.getFailed());
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
        loadCycle(cycleId, farmId);   // validates ownership + non-deletion
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
    public HarvestResponse logHarvest(UUID cycleId, HarvestRequest req, UUID farmId) {
        CropCycle cycle = loadCycle(cycleId, farmId);
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

    private CropCycle loadCycle(UUID id, UUID farmId) {
        return cycleRepo.findByIdAndFarmIdLive(id, farmId)
                .orElseThrow(() -> new ResourceNotFoundException("Crop cycle not found"));
    }
}