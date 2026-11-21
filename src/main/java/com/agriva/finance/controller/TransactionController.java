package com.agriva.finance.controller;

import com.agriva.finance.model.dto.request.TransactionRequest;
import com.agriva.finance.model.dto.response.TransactionResponse;
import com.agriva.finance.model.dto.response.TransactionSummaryResponse;
import com.agriva.shared.models.dto.PageResponse;
import com.agriva.shared.security.AuthenticatedUser;
import com.agriva.finance.service.TransactionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<TransactionResponse> create(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                transactionService.create(request, u.farmId(), u.userId()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<TransactionResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(PageResponse.of(transactionService.list(
                u.farmId(),
                PageRequest.of(page, Math.min(size, 10),
                        Sort.by(Sort.Direction.DESC, "occurredAt")))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getOne(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.get(id, u.farmId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponse> update(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.ok(transactionService.update(id, request, u.farmId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        transactionService.delete(id, u.farmId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<TransactionSummaryResponse> summary(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.summary(id, u.farmId()));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats(@AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(transactionService.getStats(u.farmId()));
    }

    @GetMapping("/export")
    public void exportToCsv(
            @AuthenticationPrincipal AuthenticatedUser u,
            HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"transactions.csv\"");
        try (PrintWriter writer = response.getWriter()) {
            transactionService.exportToCsv(u.farmId(), writer);
        }
    }

    @PostMapping("/backfill/from-income")
    public ResponseEntity<Map<String, Object>> backfillFromIncome(
            @AuthenticationPrincipal AuthenticatedUser u) {
        int created = transactionService.backfillFromIncome(u.farmId(), u.userId());
        return ResponseEntity.ok(Map.of(
                "farmId", u.farmId(),
                "transactionsCreated", created
        ));
    }
}