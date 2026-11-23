package com.agriva.feeding.controller;

import com.agriva.feeding.model.dto.request.FormulationRequest;
import com.agriva.feeding.model.dto.response.FormulationResponse;
import com.agriva.feeding.service.FormulationService;
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
@RequestMapping("/api/v1/feeding/formulations")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agriva.feeding.formulation.enabled",
        havingValue = "true", matchIfMissing = true)
public class FormulationController {

    private final FormulationService formulationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<FormulationResponse> create(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody FormulationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(formulationService.createFormulation(request, u.farmId(), u.userId()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<FormulationResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(formulationService.listFormulations(
                u.farmId(),
                PageRequest.of(page, Math.min(size, 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FormulationResponse> get(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(formulationService.getFormulation(id, u.farmId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        formulationService.deleteFormulation(id, u.farmId(), u.userId());
        return ResponseEntity.noContent().build();
    }
}