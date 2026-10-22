package com.agriva.controller;

import com.agriva.model.dto.auth.TokenValidationResponse;
import com.agriva.model.dto.crop.*;
import com.agriva.security.RoleConstants;
import com.agriva.service.AuthService;
import com.agriva.service.CropManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final AuthService authService;

    @GetMapping("/fields")
    public ResponseEntity<List<FieldResponse>> getFields(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(cropService.getFields(authService.validateToken(authHeader).getFarmId()));
    }

    @PostMapping("/fields")
    public ResponseEntity<FieldResponse> createField(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody FieldRequest req) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.status(HttpStatus.CREATED).body(cropService.createField(req, token.getFarmId()));
    }

    @PostMapping(value = "/fields/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CropBulkUploadResponse> bulkUploadFields(
            @RequestHeader("Authorization") String authHeader,
            @RequestPart("file") MultipartFile file) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.status(HttpStatus.CREATED).body(cropService.bulkUploadFields(file, token.getFarmId()));
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<CropResponse>> getCrops(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(cropService.getCrops(authService.validateToken(authHeader).getFarmId()));
    }

    @PostMapping("/catalog")
    public ResponseEntity<CropResponse> createCrop(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CropRequest req) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.status(HttpStatus.CREATED).body(cropService.createCrop(req, token.getFarmId()));
    }

    @PostMapping(value = "/catalog/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CropBulkUploadResponse> bulkUploadCrops(
            @RequestHeader("Authorization") String authHeader,
            @RequestPart("file") MultipartFile file) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.status(HttpStatus.CREATED).body(cropService.bulkUploadCrops(file, token.getFarmId()));
    }

    @GetMapping("/cycles")
    public ResponseEntity<List<CropCycleResponse>> getCycles(@RequestHeader("Authorization") String authHeader) {
        return ResponseEntity.ok(cropService.getCycles(authService.validateToken(authHeader).getFarmId()));
    }

    @GetMapping("/cycles/{id}")
    public ResponseEntity<CropCycleResponse> getCycle(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        return ResponseEntity.ok(cropService.getCycle(id, authService.validateToken(authHeader).getFarmId()));
    }

    @PostMapping("/cycles")
    public ResponseEntity<CropCycleResponse> createCycle(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CropCycleRequest req) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.status(HttpStatus.CREATED).body(cropService.createCycle(req, token.getFarmId()));
    }

    @PatchMapping("/cycles/{id}/status")
    public ResponseEntity<CropCycleResponse> updateStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestParam String status) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.ok(cropService.updateCycleStatus(id, status, token.getFarmId()));
    }

    @PostMapping(value = "/cycles/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<CropBulkUploadResponse> bulkUploadCycles(
            @RequestHeader("Authorization") String authHeader,
            @RequestPart("file") MultipartFile file) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        String email = authService.getUserById(token.getUserId()).getEmail();
        return ResponseEntity.accepted().body(cropService.bulkUploadCycles(
                file, token.getFarmId(), token.getUserId(), email));
    }

    @GetMapping("/cycles/staging")
    public ResponseEntity<Page<StagedCropCycleResponse>> listStaging(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.ok(cropService.listStaging(
                token.getFarmId(), status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt"))));
    }

    @GetMapping("/cycles/staging/stats")
    public ResponseEntity<Map<String, Long>> stagingStats(
            @RequestHeader("Authorization") String authHeader) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.ok(cropService.stagingStats(token.getFarmId()));
    }

    @GetMapping("/cycles/{id}/activities")
    public ResponseEntity<List<CropActivityResponse>> getActivities(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(cropService.getActivities(id, token.getFarmId()));
    }

    @PostMapping("/cycles/{id}/activities")
    public ResponseEntity<CropActivityResponse> logActivity(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @Valid @RequestBody CropActivityRequest req) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                cropService.logActivity(id, req, token.getFarmId(), token.getUserId()));
    }

    @PostMapping("/cycles/{id}/harvest")
    public ResponseEntity<HarvestResponse> logHarvest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @Valid @RequestBody HarvestRequest req) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                cropService.logHarvest(id, req, token.getFarmId()));
    }

    private void requireMaster(TokenValidationResponse token) {
        if (!RoleConstants.MASTER.equals(token.getRole()) && !RoleConstants.ADMIN.equals(token.getRole())) {
            throw new com.agriva.exception.PersonnelAccessDeniedException("MASTER or ADMIN required");
        }
    }
}