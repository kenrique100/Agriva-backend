package com.agriva.feeding.controller;

import com.agriva.feeding.model.dto.request.DemandForecastRequest;
import com.agriva.feeding.model.dto.request.ProduceFeedFromForecastRequest;
import com.agriva.feeding.model.dto.request.UpdateDemandForecastRequest;
import com.agriva.feeding.model.dto.response.DemandForecastResponse;
import com.agriva.feeding.model.dto.response.ProduceFeedFromForecastResponse;
import com.agriva.feeding.service.ForecastService;
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
@RequestMapping("/api/v1/feeding/forecasts")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agriva.feeding.forecast.enabled",
        havingValue = "true", matchIfMissing = true)
public class ForecastController {

    private final ForecastService forecastService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<DemandForecastResponse> create(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody DemandForecastRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(forecastService.createForecast(request, u.farmId(), u.userId()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<DemandForecastResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(forecastService.listForecasts(
                u.farmId(),
                PageRequest.of(page, Math.min(size, 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DemandForecastResponse> get(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(forecastService.getForecast(id, u.farmId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<DemandForecastResponse> update(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDemandForecastRequest request) {
        return ResponseEntity.ok(forecastService.updateForecast(
                id, request, u.farmId(), u.userId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        forecastService.deleteForecast(id, u.farmId(), u.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/produce-feed")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<ProduceFeedFromForecastResponse> produceFeed(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ProduceFeedFromForecastRequest request) {
        ProduceFeedFromForecastResponse resp = forecastService.produceFeedFromForecast(
                id, request, u.farmId(), u.userId(), idempotencyKey);
        // 201 for new, 200 for idempotent replay.
        HttpStatus status = resp.isReplayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(resp);
    }
}