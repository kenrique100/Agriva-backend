package com.agriva.feeding.controller;

import com.agriva.feeding.model.dto.request.AdjustIngredientStockRequest;
import com.agriva.feeding.model.dto.request.IngredientRequest;
import com.agriva.feeding.model.dto.request.IngredientStockReplenishRequest;
import com.agriva.feeding.model.dto.request.WriteOffIngredientStockRequest;
import com.agriva.feeding.model.dto.response.IngredientResponse;
import com.agriva.feeding.model.dto.response.IngredientStatsResponse;
import com.agriva.feeding.model.dto.response.IngredientStockAdjustmentResponse;
import com.agriva.feeding.service.IngredientService;
import com.agriva.feeding.utils.IngredientCrudeProteinResolver;
import com.agriva.shared.models.dto.BatchStatusResponse;
import com.agriva.shared.models.dto.BatchUploadResponse;
import com.agriva.shared.models.dto.PageResponse;
import com.agriva.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
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

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/feeding/ingredients")
@RequiredArgsConstructor
public class IngredientController {

    private final IngredientService ingredientService;
    private final IngredientCrudeProteinResolver cpResolver;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<IngredientResponse> create(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody IngredientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ingredientService.createIngredient(request, u.farmId(), u.userId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PageResponse<IngredientResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(ingredientService.listIngredients(
                u.farmId(),
                PageRequest.of(page, Math.min(size, 100),
                        Sort.by(Sort.Direction.ASC, "name")))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<IngredientResponse> getOne(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ingredientService.getIngredient(id, u.farmId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<IngredientResponse> update(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody IngredientRequest request) {
        return ResponseEntity.ok(
                ingredientService.updateIngredient(id, request, u.farmId(), u.userId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        ingredientService.softDelete(id, u.farmId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/replenish")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<IngredientResponse> replenish(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody IngredientStockReplenishRequest request) {
        return ResponseEntity.ok(
                ingredientService.replenishStock(id, request, u.farmId(), u.userId()));
    }

    @PutMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<IngredientResponse> adjustStock(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody AdjustIngredientStockRequest request) {
        return ResponseEntity.ok(
                ingredientService.adjustStock(id, request, u.farmId(), u.userId()));
    }

    @PostMapping("/{id}/stock/writeoff")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<IngredientResponse> writeOffStock(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody WriteOffIngredientStockRequest request) {
        return ResponseEntity.ok(
                ingredientService.writeOffStock(id, request, u.farmId(), u.userId()));
    }

    @GetMapping("/{id}/stock/adjustments")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PageResponse<IngredientStockAdjustmentResponse>> stockAdjustments(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(ingredientService.listStockAdjustments(
                id, u.farmId(),
                PageRequest.of(page, Math.min(size, 100)))));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<BatchUploadResponse> bulkUpload(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.accepted().body(
                ingredientService.bulkUpload(file, u.farmId(), u.userId(), u.email()));
    }

    @GetMapping("/bulk/{batchId}")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<BatchStatusResponse> bulkStatus(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID batchId) {
        return ResponseEntity.ok(ingredientService.bulkStatus(batchId, u.farmId()));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<IngredientStatsResponse> stats(
            @AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(ingredientService.getStats(u.farmId()));
    }

    @GetMapping("/cp-map")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Map<String, BigDecimal>> cpMap(
            @AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(cpResolver.resolveByName(u.farmId()));
    }
}