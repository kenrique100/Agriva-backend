package com.agriva.fishery.controller;

import com.agriva.fishery.model.dto.request.PondRequest;
import com.agriva.fishery.model.dto.request.PondStockRequest;
import com.agriva.fishery.model.dto.response.PondResponse;
import com.agriva.fishery.model.dto.response.PondStockResponse;
import com.agriva.fishery.service.PondService;
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
@RequestMapping("/api/v1/fishery/ponds")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agriva.fishery.enabled", havingValue = "true", matchIfMissing = true)
public class PondController {

    private final PondService pondService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PondResponse> create(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody PondRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pondService.createPond(request, u.farmId(), u.userId()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<PondResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(pondService.listPonds(
                u.farmId(),
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.ASC, "name")))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PondResponse> get(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(pondService.getPond(id, u.farmId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PondResponse> update(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody PondRequest request) {
        return ResponseEntity.ok(pondService.updatePond(id, request, u.farmId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        pondService.deletePond(id, u.farmId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PondStockResponse> addStock(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody PondStockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pondService.addStock(id, request, u.farmId()));
    }

    @GetMapping("/{id}/stock")
    public ResponseEntity<List<PondStockResponse>> getStock(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(pondService.getStock(id, u.farmId()));
    }
}