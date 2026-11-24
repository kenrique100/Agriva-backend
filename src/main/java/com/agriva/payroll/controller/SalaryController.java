package com.agriva.payroll.controller;

import com.agriva.payroll.model.dto.requests.SalaryPaymentRequest;
import com.agriva.payroll.model.dto.response.SalaryBulkUploadResponse;
import com.agriva.payroll.model.dto.response.SalaryPaymentResponse;
import com.agriva.payroll.model.dto.response.SalaryReceiptResponse;
import com.agriva.payroll.model.dto.response.StagedSalaryResponse;
import com.agriva.shared.models.dto.PageResponse;
import com.agriva.shared.security.AuthenticatedUser;
import com.agriva.personnel.service.PersonnelService;
import com.agriva.payroll.service.SalaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/salaries")
@RequiredArgsConstructor
public class SalaryController {

    private final SalaryService salaryService;
    private final PersonnelService personnelService;

    @PostMapping
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<SalaryPaymentResponse> create(
            @AuthenticationPrincipal AuthenticatedUser u,
            @Valid @RequestBody SalaryPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(salaryService.create(request, u.farmId(), u.userId()));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<SalaryBulkUploadResponse> bulkUpload(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.accepted().body(salaryService.bulkUpload(
                file, u.farmId(), u.userId(), u.email()));
    }

    @GetMapping
    public ResponseEntity<PageResponse<SalaryPaymentResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(required = false) UUID personnelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        UUID filter = personnelId;
        if (!u.isMasterOrAdmin()) {
            filter = personnelService.getOwnProfile(u.userId()).getPersonnel().getId();
        }
        return ResponseEntity.ok(PageResponse.of(salaryService.list(
                u.farmId(), filter,
                PageRequest.of(page, Math.min(size, 10),
                        Sort.by(Sort.Direction.DESC, "paymentDate")))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SalaryPaymentResponse> get(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(salaryService.get(id, u.farmId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        salaryService.delete(id, u.farmId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<SalaryReceiptResponse> receipt(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id) {
        return ResponseEntity.ok(salaryService.getReceipt(id, u.farmId()));
    }

    @GetMapping("/{id}/receipt/pdf")
    public ResponseEntity<byte[]> receiptPdf(
            @AuthenticationPrincipal AuthenticatedUser u,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean preview) {
        byte[] pdf = salaryService.getReceiptPdf(id, u.farmId());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(preview
                ? ContentDisposition.inline().filename("salary_receipt.pdf").build()
                : ContentDisposition.attachment().filename("salary_receipt.pdf").build());
        headers.setCacheControl("no-store, must-revalidate");
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @GetMapping("/staging")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<PageResponse<StagedSalaryResponse>> listStaging(
            @AuthenticationPrincipal AuthenticatedUser u,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(PageResponse.of(salaryService.listStaging(
                u.farmId(), status,
                PageRequest.of(page, Math.min(size, 10),
                        Sort.by(Sort.Direction.DESC, "submittedAt")))));
    }

    @GetMapping("/staging/stats")
    @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
    public ResponseEntity<Map<String, Long>> stagingStats(@AuthenticationPrincipal AuthenticatedUser u) {
        return ResponseEntity.ok(salaryService.stagingStats(u.farmId()));
    }
}