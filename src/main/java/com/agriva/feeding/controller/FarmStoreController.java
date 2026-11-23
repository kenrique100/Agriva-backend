package com.agriva.feeding.controller;

import com.agriva.feeding.config.FeedingProperties;
import com.agriva.feeding.mapper.FeedBatchMapper;
import com.agriva.feeding.model.dto.request.AdjustStoreRequest;
import com.agriva.feeding.model.dto.request.ApplyStoreDeltaRequest;
import com.agriva.feeding.model.dto.response.FarmStoreResponse;
import com.agriva.feeding.model.dto.response.FeedStoreAdjustmentResponse;
import com.agriva.feeding.model.entity.FarmFeedStore;
import com.agriva.feeding.service.FarmStoreService;
import com.agriva.shared.models.dto.PageResponse;
import com.agriva.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feeding/store")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agriva.feeding.production.enabled",
        havingValue = "true", matchIfMissing = true)
public class FarmStoreController {

    private final FarmStoreService farmStoreService;
    private final FeedBatchMapper feedBatchMapper;
    private final FeedingProperties feedingProperties;

    @GetMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<FarmStoreResponse> get(
            @AuthenticationPrincipal AuthenticatedUser u) {
        FarmFeedStore store = farmStoreService.get(u.farmId());
        return ResponseEntity.ok(feedBatchMapper.toStoreResponse(
                u.farmId(), store, feedingProperties.getDefaultBagSizeKg()));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<FarmStoreResponse> setAbsolute(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody AdjustStoreRequest request) {
        FarmFeedStore store = farmStoreService.setAbsolute(
                u.farmId(), request.getQuantityOnHandKg(), request.getReason(),
                request.getExpectedVersion(), u.userId());
        return ResponseEntity.ok(feedBatchMapper.toStoreResponse(
                u.farmId(), store, feedingProperties.getDefaultBagSizeKg()));
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<FarmStoreResponse> applyDelta(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody ApplyStoreDeltaRequest request) {
        FarmFeedStore store = farmStoreService.applyDelta(
                u.farmId(), request.getDeltaKg(), request.getReason(), u.userId());
        return ResponseEntity.ok(feedBatchMapper.toStoreResponse(
                u.farmId(), store, feedingProperties.getDefaultBagSizeKg()));
    }

    @GetMapping("/adjustments")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PageResponse<FeedStoreAdjustmentResponse>> adjustments(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(
                farmStoreService.listAdjustments(u.farmId(),
                        PageRequest.of(page, Math.min(size, 100)))));
    }
}