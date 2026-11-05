package com.agriva.farming.controller;

import com.agriva.farming.model.dto.requests.*;
import com.agriva.farming.model.dto.response.*;
import com.agriva.shared.models.dto.PageResponse;
import com.agriva.farming.model.enums.CropCycleStatus;
import com.agriva.shared.security.AuthenticatedUser;
import com.agriva.farming.services.CropManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crops")
@RequiredArgsConstructor
public class CropManagementController {

    private final CropManagementService cropService;

    // ---------- Cycle status update DTO ----------
    public record CycleStatusUpdate(@NotNull CropCycleStatus status) {}

    @GetMapping("/fields")
    public ResponseEntity<List<FieldResponse>> getFields(@AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(cropService.getFields(u.farmId()));
    }

    @PostMapping("/fields")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<FieldResponse> createField(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody FieldRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cropService.createField(req, u.farmId()));
    }

    @PostMapping(value = "/fields/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<CropBulkUploadResponse> bulkUploadFields(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cropService.bulkUploadFields(file, u.farmId()));
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<CropResponse>> getCrops(@AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(cropService.getCrops(u.farmId()));
    }

    @PostMapping("/catalog")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<CropResponse> createCrop(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody CropRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cropService.createCrop(req, u.farmId()));
    }

    @PostMapping(value = "/catalog/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<CropBulkUploadResponse> bulkUploadCrops(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cropService.bulkUploadCrops(file, u.farmId()));
    }

    @GetMapping("/cycles")
    public ResponseEntity<List<CropCycleResponse>> getCycles(@AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(cropService.getCycles(u.farmId()));
    }

    @GetMapping("/cycles/{id}")
    public ResponseEntity<CropCycleResponse> getCycle(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(cropService.getCycle(id, u.farmId()));
    }

    @PostMapping("/cycles")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<CropCycleResponse> createCycle(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody CropCycleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cropService.createCycle(req, u.farmId()));
    }

    /**
     * A2 fix: status update now uses @RequestBody (not query param).
     */
    @PatchMapping("/cycles/{id}/status")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<CropCycleResponse> updateStatus(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody CycleStatusUpdate req) {
        return ResponseEntity.ok(cropService.updateCycleStatus(
                id, req.status().name(), u.farmId()));
    }

    @PostMapping(value = "/cycles/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<CropBulkUploadResponse> bulkUploadCycles(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.accepted().body(cropService.bulkUploadCycles(
                file, u.farmId(), u.userId(), u.email()));
    }

    @GetMapping("/cycles/staging")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PageResponse<StagedCropCycleResponse>> listStaging(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(cropService.listStaging(
                u.farmId(), status,
                PageRequest.of(page, Math.min(size, 100),
                        Sort.by(Sort.Direction.DESC, "submittedAt")))));
    }

    @GetMapping("/cycles/staging/stats")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Map<String, Long>> stagingStats(@AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(cropService.stagingStats(u.farmId()));
    }

    @GetMapping("/cycles/{id}/activities")
    public ResponseEntity<List<CropActivityResponse>> getActivities(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(cropService.getActivities(id, u.farmId()));
    }

    @PostMapping("/cycles/{id}/activities")
    public ResponseEntity<CropActivityResponse> logActivity(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody CropActivityRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                cropService.logActivity(id, req, u.farmId(), u.userId()));
    }

    @PostMapping("/cycles/{id}/harvest")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<HarvestResponse> logHarvest(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody HarvestRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                cropService.logHarvest(id, req, u.farmId()));
    }

    @DeleteMapping("/cycles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Void> deleteCycle(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        cropService.deleteCycle(id, u.farmId());
        return ResponseEntity.noContent().build();
    }
}