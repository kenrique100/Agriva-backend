package com.agriva.controller;

import com.agriva.model.dto.auth.TokenValidationResponse;
import com.agriva.model.dto.investment.InvestmentRequest;
import com.agriva.model.dto.investment.InvestmentResponse;
import com.agriva.service.AuthService;
import com.agriva.service.InvestmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/investments")
@RequiredArgsConstructor
@Slf4j
public class InvestmentController {

    private final InvestmentService investmentService;
    private final AuthService authService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<InvestmentResponse> create(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody InvestmentRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMasterOrAdmin(token);
        return ResponseEntity.status(HttpStatus.CREATED).body(investmentService.createInvestment(
                request, token.getFarmId(), token.getUserId()));
    }

    @GetMapping
    public ResponseEntity<Page<InvestmentResponse>> list(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "investmentDate,desc") String sort) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMasterOrAdmin(token);
        return ResponseEntity.ok(investmentService.listInvestments(
                token.getFarmId(),
                PageRequest.of(page, size, parseSort(sort))));
    }

    @GetMapping("/active")
    public ResponseEntity<InvestmentResponse> getActive(
            @RequestHeader("Authorization") String authHeader) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        // Accessible to ALL farm roles — expense submitters need this for UI banners
        InvestmentResponse active = investmentService.getActive(token.getFarmId());
        return active == null
                ? ResponseEntity.noContent().build()
                : ResponseEntity.ok(active);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvestmentResponse> getOne(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMasterOrAdmin(token);
        return ResponseEntity.ok(investmentService.getInvestment(id, token.getFarmId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InvestmentResponse> update(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @Valid @RequestBody InvestmentRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMasterOrAdmin(token);
        return ResponseEntity.ok(investmentService.updateInvestment(id, request, token.getFarmId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMasterOrAdmin(token);
        investmentService.deleteInvestment(id, token.getFarmId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<InvestmentResponse> activate(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMasterOrAdmin(token);
        return ResponseEntity.ok(investmentService.activate(id, token.getFarmId()));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<InvestmentResponse> deactivate(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMasterOrAdmin(token);
        return ResponseEntity.ok(investmentService.deactivate(id, token.getFarmId()));
    }

    private void requireMasterOrAdmin(TokenValidationResponse token) {
        String role = token.getRole();
        if (!"MASTER".equals(role) && !"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only MASTER or ADMIN can access investments");
        }
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        Sort.Direction dir = (parts.length > 1 && parts[1].equalsIgnoreCase("asc"))
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, parts[0]);
    }
}