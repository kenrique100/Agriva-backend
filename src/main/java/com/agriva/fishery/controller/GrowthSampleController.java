package com.agriva.fishery.controller;

import com.agriva.fishery.model.dto.request.GrowthSampleRequest;
import com.agriva.fishery.model.dto.response.GrowthSampleResponse;
import com.agriva.fishery.service.GrowthSampleService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fishery/ponds/{pondId}/samples")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agriva.fishery.growth-samples.enabled",
        havingValue = "true", matchIfMissing = true)
public class GrowthSampleController {

    private final GrowthSampleService growthSampleService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<GrowthSampleResponse> record(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID pondId,
            @Valid @RequestBody GrowthSampleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(growthSampleService.record(pondId, request, u.farmId(), u.userId()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<GrowthSampleResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID pondId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(growthSampleService.listForPond(
                pondId, u.farmId(),
                PageRequest.of(page, Math.min(size, 100),
                        Sort.by(Sort.Direction.DESC, "sampledAt")
                                .and(Sort.by(Sort.Direction.DESC, "createdAt"))))));
    }
}