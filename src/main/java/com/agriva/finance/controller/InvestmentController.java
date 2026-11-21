package com.agriva.finance.controller;

import com.agriva.shared.models.dto.PageResponse;
import com.agriva.finance.model.dto.request.InvestmentRequest;
import com.agriva.finance.model.dto.response.InvestmentResponse;
import com.agriva.shared.security.AuthenticatedUser;
import com.agriva.finance.service.InvestmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/investments")
@RequiredArgsConstructor
public class InvestmentController {

    private static final Set<String> SORTABLE = Set.of("investmentDate", "amount", "createdAt");

    private final InvestmentService investmentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<InvestmentResponse> create(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody InvestmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(investmentService.createInvestment(request, u.farmId(), u.userId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PageResponse<InvestmentResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, // Default size 10
            @RequestParam(defaultValue = "investmentDate,desc") String sort) {
        return ResponseEntity.ok(PageResponse.of(investmentService.listInvestments(
                u.farmId(),
                // RESTRICTED TO MAX 10 RECORDS PER PAGE
                PageRequest.of(page, Math.min(size, 10), parseSort(sort)))));
    }

    @GetMapping("/active")
    public ResponseEntity<InvestmentResponse> getActive(@AuthenticationPrincipal AuthenticatedUser u) {
        InvestmentResponse active = investmentService.getActive(u.farmId());
        return active == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(active);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<InvestmentResponse> getOne(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(investmentService.getInvestment(id, u.farmId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<InvestmentResponse> update(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody InvestmentRequest request) {
        return ResponseEntity.ok(investmentService.updateInvestment(id, request, u.farmId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        investmentService.deleteInvestment(id, u.farmId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<InvestmentResponse> activate(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(investmentService.activate(id, u.farmId()));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<InvestmentResponse> deactivate(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(investmentService.deactivate(id, u.farmId()));
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        if (!SORTABLE.contains(parts[0])) {
            throw new IllegalArgumentException("Invalid sort field: " + parts[0]);
        }
        Sort.Direction dir = (parts.length > 1 && parts[1].equalsIgnoreCase("asc"))
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, parts[0]);
    }
}