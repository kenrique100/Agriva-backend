package com.agriva.feeding.controller;

import com.agriva.feeding.model.dto.request.ProductionRequest;
import com.agriva.feeding.model.dto.response.FeedBatchResponse;
import com.agriva.feeding.service.ProductionService;
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

@RestController
@RequestMapping("/api/v1/feeding")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agriva.feeding.production.enabled",
        havingValue = "true", matchIfMissing = true)
public class ProductionController {

    private final ProductionService productionService;

    @PostMapping("/productions")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<FeedBatchResponse> produce(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody ProductionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productionService.produce(request, u.farmId(), u.userId()));
    }

    @GetMapping("/productions")
    public ResponseEntity<PageResponse<FeedBatchResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(productionService.listBatches(
                u.farmId(),
                PageRequest.of(page, Math.min(size, 100),
                        Sort.by(Sort.Direction.DESC, "productionDate", "createdAt")))));
    }
}