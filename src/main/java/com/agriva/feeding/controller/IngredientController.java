package com.agriva.feeding.controller;

import com.agriva.feeding.model.dto.request.IngredientRequest;
import com.agriva.feeding.model.dto.request.IngredientStockReplenishRequest;
import com.agriva.feeding.model.dto.response.IngredientResponse;
import com.agriva.feeding.service.IngredientService;
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
@RequestMapping("/api/v1/feeding/ingredients")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agriva.feeding.ingredients.enabled",
        havingValue = "true", matchIfMissing = true)
public class IngredientController {

    private final IngredientService ingredientService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<IngredientResponse> create(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody IngredientRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ingredientService.createIngredient(request, u.farmId(), u.userId()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<IngredientResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(ingredientService.listIngredients(
                u.farmId(),
                PageRequest.of(page, Math.min(size, 100),
                        Sort.by(Sort.Direction.ASC, "name")))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IngredientResponse> get(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ingredientService.getIngredient(id, u.farmId()));
    }

    @PostMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<IngredientResponse> replenishStock(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody IngredientStockReplenishRequest request) {
        return ResponseEntity.ok(ingredientService.replenishStock(
                id, request, u.farmId(), u.userId()));
    }
}