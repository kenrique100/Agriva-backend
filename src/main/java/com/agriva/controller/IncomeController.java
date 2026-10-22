package com.agriva.controller;

import com.agriva.model.dto.auth.TokenValidationResponse;
import com.agriva.model.dto.income.*;
import com.agriva.service.AuthService;
import com.agriva.service.FileStorageService;
import com.agriva.service.IncomeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incomes")
@RequiredArgsConstructor
@Slf4j
public class IncomeController {

    private final IncomeService incomeService;
    private final AuthService authService;
    private final FileStorageService fileStorageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<IncomeSubmissionAck> submit(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestPart("request") IncomeRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        TokenValidationResponse token = authService.validateToken(authHeader);

        if (file != null && !file.isEmpty()) {
            request.setReceipt(fileStorageService.storeFile(file));
        }

        return ResponseEntity.accepted().body(incomeService.submitIncome(
                request, token.getFarmId(), token.getUserId(), token.getUsername()));
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<IncomeBulkUploadResponse> bulkUpload(
            @RequestHeader("Authorization") String authHeader,
            @RequestPart("file") MultipartFile file) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.accepted().body(incomeService.bulkUpload(
                file, token.getFarmId(), token.getUserId(), token.getUsername()));
    }

    @GetMapping
    public ResponseEntity<Page<IncomeResponse>> list(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "incomeDate,desc") String sort) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(incomeService.listIncome(
                token.getFarmId(),
                PageRequest.of(page, size, parseSort(sort))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncomeResponse> getOne(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(incomeService.getIncome(id, token.getFarmId()));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IncomeResponse> update(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @Valid @RequestPart("request") IncomeRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) {

        TokenValidationResponse token = authService.validateToken(authHeader);

        if (file != null && !file.isEmpty()) {
            request.setReceipt(fileStorageService.storeFile(file));
        }

        return ResponseEntity.ok(incomeService.updateIncome(id, request, token.getFarmId()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        incomeService.deleteIncome(id, token.getFarmId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/staging/stats")
    public ResponseEntity<Map<String, Long>> stagingStats(
            @RequestHeader("Authorization") String authHeader) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(incomeService.stagingStats(token.getFarmId()));
    }

    @GetMapping("/staging")
    public ResponseEntity<Page<IncomeStagingResponse>> listStaging(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        return ResponseEntity.ok(incomeService.listStaging(
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