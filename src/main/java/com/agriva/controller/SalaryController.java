package com.agriva.controller;

import com.agriva.exception.PersonnelAccessDeniedException;
import com.agriva.model.dto.auth.TokenValidationResponse;
import com.agriva.model.dto.personnel.PersonnelProfileResponse;
import com.agriva.model.dto.salary.*;
import com.agriva.security.RoleConstants;
import com.agriva.service.AuthService;
import com.agriva.service.PersonnelService;
import com.agriva.service.SalaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/salaries")
@RequiredArgsConstructor
public class SalaryController {

    private final SalaryService salaryService;
    private final AuthService authService;
    private final PersonnelService personnelService;

    @PostMapping
    public ResponseEntity<SalaryPaymentResponse> create(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody SalaryPaymentRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(salaryService.create(request, token.getFarmId(), token.getUserId()));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<SalaryBulkUploadResponse> bulkUpload(
            @RequestHeader("Authorization") String authHeader,
            @RequestPart("file") MultipartFile file) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.accepted().body(salaryService.bulkUpload(
                file, token.getFarmId(), token.getUserId(),
                authService.getUserById(token.getUserId()).getEmail()));
    }

    @GetMapping
    public ResponseEntity<Page<SalaryPaymentResponse>> list(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) UUID personnelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        UUID filter = personnelId;
        if (!isMasterOrAdmin(token)) {
            PersonnelProfileResponse own = personnelService.getOwnProfile(token.getUserId());
            filter = own.getPersonnel().getId();
        }
        return ResponseEntity.ok(salaryService.list(
                token.getFarmId(), filter,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "paymentDate"))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SalaryPaymentResponse> get(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(salaryService.get(id, token.getFarmId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        salaryService.delete(id, token.getFarmId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<SalaryReceiptResponse> receipt(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(salaryService.getReceipt(id, token.getFarmId()));
    }

    @GetMapping("/{id}/receipt/pdf")
    public ResponseEntity<byte[]> receiptPdf(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean preview) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        byte[] pdf = salaryService.getReceiptPdf(id, token.getFarmId());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(preview
                ? ContentDisposition.inline().filename("salary_receipt.pdf").build()
                : ContentDisposition.attachment().filename("salary_receipt.pdf").build());
        headers.setCacheControl("no-store, must-revalidate");
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @GetMapping("/staging")
    public ResponseEntity<Page<StagedSalaryResponse>> listStaging(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.ok(salaryService.listStaging(
                token.getFarmId(), status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt"))));
    }

    @GetMapping("/staging/stats")
    public ResponseEntity<Map<String, Long>> stagingStats(
            @RequestHeader("Authorization") String authHeader) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.ok(salaryService.stagingStats(token.getFarmId()));
    }

    private boolean isMasterOrAdmin(TokenValidationResponse t) {
        return RoleConstants.MASTER.equals(t.getRole()) || RoleConstants.ADMIN.equals(t.getRole());
    }

    private void requireMaster(TokenValidationResponse token) {
        if (!isMasterOrAdmin(token)) throw new PersonnelAccessDeniedException("MASTER or ADMIN required");
    }
}