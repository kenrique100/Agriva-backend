package com.agriva.controller;

import com.agriva.model.dto.common.CursorPageResponse;
import com.agriva.model.dto.common.PageResponse;
import com.agriva.model.dto.expense.*;
import com.agriva.security.AuthenticatedUser;
import com.agriva.service.ExpenseService;
import com.agriva.service.FileStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private static final Set<String> SORTABLE = Set.of("expenseDate", "amountPaid", "createdAt");

    private final ExpenseService expenseService;
    private final FileStorageService fileStorageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<ExpenseSubmissionAck> submit(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestPart("request") ExpenseRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        if (file != null && !file.isEmpty()) {
            request.setReceipt(fileStorageService.storeFile(file));
        }
        return ResponseEntity.accepted().body(expenseService.submitExpense(
                request, u.farmId(), u.userId(), u.email()));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<ExpenseBulkUploadResponse> bulkUpload(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.accepted().body(expenseService.bulkUpload(
                file, u.farmId(), u.userId(), u.email()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ExpenseResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "expenseDate,desc") String sort) {
        return ResponseEntity.ok(PageResponse.of(expenseService.listExpenses(
                u.farmId(),
                PageRequest.of(page, Math.min(size, 100), parseSort(sort)))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponse> getOne(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(expenseService.getExpense(id, u.farmId()));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExpenseResponse> update(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestPart("request") ExpenseRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        if (file != null && !file.isEmpty()) {
            request.setReceipt(fileStorageService.storeFile(file));
        }
        return ResponseEntity.ok(expenseService.updateExpense(id, request, u.farmId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        expenseService.deleteExpense(id, u.farmId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/staging/stats")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Map<String, Long>> stagingStats(@AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(expenseService.stagingStats(u.farmId()));
    }

    @GetMapping("/staging")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PageResponse<ExpenseStagingResponse>> listStaging(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(expenseService.listStaging(
                u.farmId(), status,
                PageRequest.of(page, Math.min(size, 100),
                        Sort.by(Sort.Direction.DESC, "submittedAt")))));
    }

    @GetMapping("/cursor")
    public ResponseEntity<CursorPageResponse<ExpenseResponse>> listByCursor(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                expenseService.listExpensesByCursor(u.farmId(), cursor, size));
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