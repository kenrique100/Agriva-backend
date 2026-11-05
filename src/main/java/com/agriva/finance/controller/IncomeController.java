package com.agriva.finance.controller;

import com.agriva.finance.model.dto.request.IncomeRequest;
import com.agriva.finance.model.dto.response.IncomeBulkUploadResponse;
import com.agriva.finance.model.dto.response.IncomeResponse;
import com.agriva.finance.model.dto.response.IncomeStagingResponse;
import com.agriva.finance.model.dto.response.IncomeSubmissionAck;
import com.agriva.shared.models.dto.CursorPageResponse;
import com.agriva.shared.models.dto.PageResponse;
import com.agriva.shared.security.AuthenticatedUser;
import com.agriva.file.service.FileStorageService;
import com.agriva.finance.service.IncomeService;
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

    // ----- Write endpoints (unchanged) -----

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

    // ----- Read endpoints: OFFSET (backward-compat) -----

    @GetMapping
    public ResponseEntity<PageResponse<IncomeResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "incomeDate,desc") String sort) {
        return ResponseEntity.ok(PageResponse.of(incomeService.listIncome(
                u.farmId(),
                PageRequest.of(page, Math.min(size, 100), parseSort(sort)))));
    }

    // ----- Read endpoints: CURSOR (preferred for large lists) -----

    /**
     * Keyset (cursor) pagination.
     * <p>
     * First call: GET /incomes/cursor?size=20
     *   → { content: [...20 rows], nextCursor: "…", hasNext: true, size: 20 }
     * <p>
     * Next call: GET /incomes/cursor?size=20&cursor=… (same cursor string)
     * <p>
     * When hasNext is false, nextCursor is null — stop.
     * <p>
     * Why this endpoint exists: OFFSET /incomes?page=500 gets progressively
     * slower (Postgres scans and discards page*size rows). Cursor is O(1)
     * per page because it seeks directly to the row position via the
     * (farm_id, income_date DESC, id DESC) composite index.
     */
    @GetMapping("/cursor")
    public ResponseEntity<CursorPageResponse<IncomeResponse>> listByCursor(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                incomeService.listIncomeByCursor(u.farmId(), cursor, size));
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
    public ResponseEntity<Map<String, Long>> stagingStats(
            @AuthenticationPrincipal AuthenticatedUser u) {
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
                PageRequest.of(page, Math.min(size, 100),
                        Sort.by(Sort.Direction.DESC, "submittedAt")))));
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