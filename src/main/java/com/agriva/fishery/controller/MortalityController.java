package com.agriva.fishery.controller;

import com.agriva.fishery.model.dto.request.MortalityRequest;
import com.agriva.fishery.model.dto.response.MortalityResponse;
import com.agriva.fishery.service.MortalityService;
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
@RequestMapping("/api/v1/fishery/ponds/{pondId}/mortality")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agriva.fishery.mortality.enabled",
        havingValue = "true", matchIfMissing = true)
public class MortalityController {

    private final MortalityService mortalityService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<MortalityResponse> record(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID pondId,
            @Valid @RequestBody MortalityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mortalityService.record(pondId, request, u.farmId(), u.userId()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<MortalityResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID pondId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(mortalityService.listForPond(
                pondId, u.farmId(),
                PageRequest.of(page, Math.min(size, 100),
                        Sort.by(Sort.Direction.DESC, "recordedAt")
                                .and(Sort.by(Sort.Direction.DESC, "createdAt"))))));
    }
}