package com.agriva.controller;

import com.agriva.model.dto.auth.TokenValidationResponse;
import com.agriva.model.dto.expense.*;
import com.agriva.service.AuthService;
import com.agriva.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
@Slf4j
public class ExpenseController {

    private final ExpenseService expenseService;
    private final AuthService authService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<ExpenseSubmissionAck> submit(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody ExpenseRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.accepted().body(expenseService.submitExpense(
                request, token.getFarmId(), token.getUserId(), token.getUsername()));
    }

    @PostMapping(value = "/bulk", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<ExpenseBulkUploadResponse> bulkUpload(
            @RequestHeader("Authorization") String authHeader,
            @RequestPart("file") MultipartFile file) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.accepted().body(expenseService.bulkUpload(
                file, token.getFarmId(), token.getUserId(), token.getUsername()));
    }

    @GetMapping
    public ResponseEntity<Page<ExpenseResponse>> list(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "expenseDate,desc") String sort) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(expenseService.listExpenses(
                token.getFarmId(),
                PageRequest.of(page, size, parseSort(sort))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponse> getOne(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(expenseService.getExpense(id, token.getFarmId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExpenseResponse> update(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @Valid @RequestBody ExpenseRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(expenseService.updateExpense(id, request, token.getFarmId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        expenseService.deleteExpense(id, token.getFarmId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/staging/stats")
    public ResponseEntity<Map<String, Long>> stagingStats(
            @RequestHeader("Authorization") String authHeader) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(expenseService.stagingStats(token.getFarmId()));
    }

    @GetMapping("/staging")
    public ResponseEntity<Page<ExpenseStagingResponse>> listStaging(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(expenseService.listStaging(
                token.getFarmId(), status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt"))));
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        Sort.Direction dir = (parts.length > 1 && parts[1].equalsIgnoreCase("asc"))
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, parts[0]);
    }
}