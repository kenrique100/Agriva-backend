package com.agriva.poultry.controller;

import com.agriva.poultry.model.dto.request.BatchStatusUpdateRequest;
import com.agriva.poultry.model.dto.request.PoultryBatchRequest;
import com.agriva.poultry.model.dto.response.PoultryBatchResponse;
import com.agriva.poultry.service.PoultryBatchService;
import com.agriva.shared.models.dto.PageResponse;
import com.agriva.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/poultry")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agriva.poultry.enabled", havingValue = "true", matchIfMissing = true)
public class PoultryBatchController {

    private final PoultryBatchService batchService;

    @PostMapping("/houses/{houseId}/batches")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PoultryBatchResponse> create(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID houseId,
            @Valid @RequestBody PoultryBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(batchService.createBatch(houseId, request, u.farmId(), u.userId()));
    }

    @GetMapping("/houses/{houseId}/batches")
    public ResponseEntity<List<PoultryBatchResponse>> listForHouse(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID houseId) {
        return ResponseEntity.ok(batchService.listBatchesByHouse(houseId, u.farmId()));
    }

    @GetMapping("/batches")
    public ResponseEntity<PageResponse<PoultryBatchResponse>> listAll(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(batchService.listBatchesByFarm(
                u.farmId(),
                PageRequest.of(page, Math.min(size, 100),
                        Sort.by(Sort.Direction.DESC, "arrivalDate")))));
    }

    @GetMapping("/batches/{id}")
    public ResponseEntity<PoultryBatchResponse> get(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(batchService.getBatch(id, u.farmId()));
    }

    @PatchMapping("/batches/{id}/status")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PoultryBatchResponse> updateStatus(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody BatchStatusUpdateRequest request) {
        return ResponseEntity.ok(batchService.updateStatus(id, request.getStatus(), u.farmId()));
    }
}