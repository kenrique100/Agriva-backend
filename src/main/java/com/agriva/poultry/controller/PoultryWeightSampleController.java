package com.agriva.poultry.controller;

import com.agriva.poultry.model.dto.request.WeightSampleRequest;
import com.agriva.poultry.model.dto.response.WeightSampleResponse;
import com.agriva.poultry.service.PoultryWeightSampleService;
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
@RequestMapping("/api/v1/poultry/batches/{batchId}/weights")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agriva.poultry.weight-samples.enabled",
        havingValue = "true", matchIfMissing = true)
public class PoultryWeightSampleController {

    private final PoultryWeightSampleService sampleService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<WeightSampleResponse> record(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID batchId,
            @Valid @RequestBody WeightSampleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sampleService.record(batchId, request, u.farmId(), u.userId()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<WeightSampleResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID batchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(sampleService.listForBatch(
                batchId, u.farmId(),
                PageRequest.of(page, Math.min(size, 100),
                        Sort.by(Sort.Direction.DESC, "sampledAt")
                                .and(Sort.by(Sort.Direction.DESC, "createdAt"))))));
    }
}