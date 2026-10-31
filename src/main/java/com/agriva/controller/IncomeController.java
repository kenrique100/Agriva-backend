package com.agriva.controller;

import com.agriva.model.dto.common.PageResponse;
import com.agriva.model.dto.income.*;
import com.agriva.security.AuthenticatedUser;
import com.agriva.service.FileStorageService;
import com.agriva.service.IncomeService;
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
@RequestMapping("/api/v1/incomes")
@RequiredArgsConstructor
public class IncomeController {

    private static final Set<String> SORTABLE = Set.of("incomeDate", "amountReceived", "createdAt");

    private final IncomeService incomeService;
    private final FileStorageService fileStorageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<IncomeSubmissionAck> submit(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestPart("request") IncomeRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        if (file != null && !file.isEmpty()) {
            request.setReceipt(fileStorageService.storeFile(file));
        }
        return ResponseEntity.accepted().body(incomeService.submitIncome(
                request, u.farmId(), u.userId(), u.email()));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<IncomeBulkUploadResponse> bulkUpload(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.accepted().body(incomeService.bulkUpload(
                file, u.farmId(), u.userId(), u.email()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<IncomeResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "incomeDate,desc") String sort) {
        return ResponseEntity.ok(PageResponse.of(
                incomeService.listIncome(u.farmId(), PageRequest.of(page, Math.min(size, 100), parseSort(sort)))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncomeResponse> getOne(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(incomeService.getIncome(id, u.farmId()));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IncomeResponse> update(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @Valid @RequestPart("request") IncomeRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        if (file != null && !file.isEmpty()) {
            request.setReceipt(fileStorageService.storeFile(file));
        }
        return ResponseEntity.ok(incomeService.updateIncome(id, request, u.farmId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        incomeService.deleteIncome(id, u.farmId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/staging/stats")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Map<String, Long>> stagingStats(@AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(incomeService.stagingStats(u.farmId()));
    }

    @GetMapping("/staging")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PageResponse<IncomeStagingResponse>> listStaging(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(PageResponse.of(incomeService.listStaging(
                u.farmId(), status,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "submittedAt")))));
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