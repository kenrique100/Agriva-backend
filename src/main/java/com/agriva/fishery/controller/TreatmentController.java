package com.agriva.fishery.controller;

import com.agriva.fishery.model.dto.request.TreatmentRequest;
import com.agriva.fishery.model.dto.response.TreatmentResponse;
import com.agriva.fishery.service.TreatmentService;
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
@RequestMapping("/api/v1/fishery/ponds/{pondId}/treatments")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agriva.fishery.treatments.enabled",
        havingValue = "true", matchIfMissing = true)
public class TreatmentController {

    private final TreatmentService treatmentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<TreatmentResponse> record(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID pondId,
            @Valid @RequestBody TreatmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(treatmentService.record(pondId, request, u.farmId(), u.userId()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<TreatmentResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID pondId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(treatmentService.listForPond(
                pondId, u.farmId(),
                PageRequest.of(page, Math.min(size, 100),
                        Sort.by(Sort.Direction.DESC, "administeredAt")
                                .and(Sort.by(Sort.Direction.DESC, "createdAt"))))));
    }

    @DeleteMapping("/{treatmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID pondId,
            @PathVariable UUID treatmentId) {
        treatmentService.delete(pondId, treatmentId, u.farmId());
        return ResponseEntity.noContent().build();
    }
}