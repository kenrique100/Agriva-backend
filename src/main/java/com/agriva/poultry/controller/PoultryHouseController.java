package com.agriva.poultry.controller;

import com.agriva.poultry.model.dto.request.PoultryHouseRequest;
import com.agriva.poultry.model.dto.response.PoultryHouseResponse;
import com.agriva.poultry.service.PoultryHouseService;
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
@RequestMapping("/api/v1/poultry/houses")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agriva.poultry.enabled", havingValue = "true", matchIfMissing = true)
public class PoultryHouseController {

    private final PoultryHouseService houseService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PoultryHouseResponse> create(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody PoultryHouseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(houseService.createHouse(request, u.farmId(), u.userId()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<PoultryHouseResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(houseService.listHouses(
                u.farmId(),
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.ASC, "name")))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PoultryHouseResponse> get(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(houseService.getHouse(id, u.farmId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PoultryHouseResponse> update(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody PoultryHouseRequest request) {
        return ResponseEntity.ok(houseService.updateHouse(id, request, u.farmId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        houseService.deleteHouse(id, u.farmId());
        return ResponseEntity.noContent().build();
    }
}