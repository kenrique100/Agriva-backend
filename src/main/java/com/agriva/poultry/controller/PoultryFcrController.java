package com.agriva.poultry.controller;

import com.agriva.poultry.model.dto.response.BatchFcrResponse;
import com.agriva.poultry.service.FcrService;
import com.agriva.shared.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/poultry/batches/{batchId}/fcr")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agriva.poultry.fcr.enabled",
        havingValue = "true", matchIfMissing = true)
public class PoultryFcrController {

    private final FcrService fcrService;

    @GetMapping
    public ResponseEntity<BatchFcrResponse> compute(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID batchId) {
        return ResponseEntity.ok(fcrService.compute(batchId, u.farmId()));
    }
}