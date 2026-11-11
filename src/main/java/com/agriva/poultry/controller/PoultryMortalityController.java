package com.agriva.poultry.controller;

import com.agriva.poultry.model.dto.request.MortalityRecordRequest;
import com.agriva.poultry.model.dto.response.MortalityRecordResponse;
import com.agriva.poultry.service.PoultryMortalityService;
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
@RequestMapping("/api/v1/poultry/batches/{batchId}/mortality")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agriva.poultry.mortality.enabled",
        havingValue = "true", matchIfMissing = true)
public class PoultryMortalityController {

    private final PoultryMortalityService mortalityService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<MortalityRecordResponse> record(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID batchId,
            @Valid @RequestBody MortalityRecordRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mortalityService.record(batchId, request, u.farmId(), u.userId()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<MortalityRecordResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID batchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(mortalityService.listForBatch(
                batchId, u.farmId(),
                PageRequest.of(page, Math.min(size, 100),
                        Sort.by(Sort.Direction.DESC, "recordedAt")
                                .and(Sort.by(Sort.Direction.DESC, "createdAt"))))));
    }
}