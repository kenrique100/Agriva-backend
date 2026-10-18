package com.agriva.controller;

import com.agriva.model.dto.auth.TokenValidationResponse;
import com.agriva.model.dto.transaction.*;
import com.agriva.service.AuthService;
import com.agriva.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;
    private final AuthService authService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<TransactionResponse> create(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody TransactionRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                transactionService.create(request, token.getFarmId(), token.getUserId()));
    }

    @GetMapping
    public ResponseEntity<Page<TransactionResponse>> list(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(transactionService.list(
                token.getFarmId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getOne(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(transactionService.get(id, token.getFarmId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> update(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @Valid @RequestBody TransactionRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(transactionService.update(id, request, token.getFarmId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        transactionService.delete(id, token.getFarmId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<TransactionSummaryResponse> summary(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(transactionService.summary(id, token.getFarmId()));
    }

    @PostMapping("/backfill/from-income")
    public ResponseEntity<Map<String, Object>> backfillFromIncome(
            @RequestHeader("Authorization") String authHeader) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        int created = transactionService.backfillFromIncome(
                token.getFarmId(), token.getUserId());
        return ResponseEntity.ok(Map.of(
                "farmId", token.getFarmId(),
                "transactionsCreated", created
        ));
    }
}