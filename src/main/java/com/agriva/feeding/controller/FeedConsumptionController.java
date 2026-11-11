package com.agriva.feeding.controller;

import com.agriva.feeding.model.dto.request.FeedingRequest;
import com.agriva.feeding.model.dto.response.FeedingEventResponse;
import com.agriva.feeding.port.ConsumerType;
import com.agriva.feeding.service.FeedConsumptionService;
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
@RequestMapping("/api/v1/feeding/feedings")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agriva.feeding.consumption.enabled",
        havingValue = "true", matchIfMissing = true)
public class FeedConsumptionController {

    private final FeedConsumptionService consumptionService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<FeedingEventResponse> feed(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody FeedingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(consumptionService.feed(request, u.farmId(), u.userId()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<FeedingEventResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(required = false) ConsumerType consumerType,
            @RequestParam(required = false) UUID consumerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(consumptionService.listFeedings(
                u.farmId(), consumerType, consumerId,
                PageRequest.of(page, Math.min(size, 100),
                        Sort.by(Sort.Direction.DESC, "fedAt")
                                .and(Sort.by(Sort.Direction.DESC, "createdAt"))))));
    }
}