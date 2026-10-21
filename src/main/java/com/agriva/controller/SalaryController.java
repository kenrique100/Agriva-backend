package com.agriva.controller;

import com.agriva.exception.PersonnelAccessDeniedException;
import com.agriva.model.dto.auth.TokenValidationResponse;
import com.agriva.model.dto.salary.*;
import com.agriva.security.RoleConstants;
import com.agriva.service.AuthService;
import com.agriva.service.SalaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/salaries")
@RequiredArgsConstructor
public class SalaryController {

    private final SalaryService salaryService;
    private final AuthService authService;

    @PostMapping
    public ResponseEntity<SalaryPaymentResponse> create(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody SalaryPaymentRequest request) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(salaryService.create(request, token.getFarmId(), token.getUserId()));
    }

    @GetMapping
    public ResponseEntity<Page<SalaryPaymentResponse>> list(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) UUID personnelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        requireMaster(token);
        return ResponseEntity.ok(salaryService.list(
                token.getFarmId(), personnelId,
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

    private void requireMaster(TokenValidationResponse token) {
        if (!RoleConstants.MASTER.equals(token.getRole())
                && !RoleConstants.ADMIN.equals(token.getRole())) {
            throw new PersonnelAccessDeniedException("MASTER or ADMIN required");
        }
    }
}