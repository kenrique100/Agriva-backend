package com.agriva.feeding.controller;

import com.agriva.feeding.model.dto.request.AdjustStoreRequest;
import com.agriva.feeding.model.dto.request.StoreDeltaRequest;
import com.agriva.feeding.model.dto.response.FarmStoreResponse;
import com.agriva.feeding.model.dto.response.FeedStoreAdjustmentResponse;
import com.agriva.feeding.service.FarmStoreService;
import com.agriva.feeding.service.ProductionService;
import com.agriva.shared.models.dto.PageResponse;
import com.agriva.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feeding/store")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agriva.feeding.enabled",
        havingValue = "true", matchIfMissing = true)
public class FarmStoreController {

    private final FarmStoreService farmStoreService;
    private final ProductionService productionService;

    @GetMapping
    public ResponseEntity<FarmStoreResponse> getStore(
            @AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(productionService.getStore(u.farmId()));
    }

    @GetMapping("/adjustments")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PageResponse<FeedStoreAdjustmentResponse>> listAdjustments(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(farmStoreService.listAdjustments(
                u.farmId(),
                PageRequest.of(page, Math.min(size, 100),
                        Sort.by(Sort.Direction.DESC, "performedAt")))));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<FarmStoreResponse> setAbsolute(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody AdjustStoreRequest request) {
        farmStoreService.setAbsolute(
                u.farmId(),
                request.getQuantityOnHandKg(),
                request.getReason(),
                request.getExpectedVersion(),
                u.userId());
        return ResponseEntity.ok(productionService.getStore(u.farmId()));
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<FarmStoreResponse> applyDelta(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody StoreDeltaRequest request) {
        farmStoreService.applyDelta(
                u.farmId(),
                request.getDeltaKg(),
                request.getReason(),
                u.userId());
        return ResponseEntity.ok(productionService.getStore(u.farmId()));
    }
}