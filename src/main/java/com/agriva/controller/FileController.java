package com.agriva.controller;

import com.agriva.model.dto.auth.TokenValidationResponse;
import com.agriva.repository.ExpenseRepository;
import com.agriva.repository.IncomeRepository;
import com.agriva.repository.PersonnelRepository;
import com.agriva.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@Slf4j
public class FileController {

    private final Path fileStorageLocation;
    private final AuthService authService;
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final PersonnelRepository personnelRepository;

    public FileController(
            @org.springframework.beans.factory.annotation.Value("${agriva.uploads.dir}") String uploadDir,
            AuthService authService,
            IncomeRepository incomeRepository,
            ExpenseRepository expenseRepository,
            PersonnelRepository personnelRepository
    ) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.authService = authService;
        this.incomeRepository = incomeRepository;
        this.expenseRepository = expenseRepository;
        this.personnelRepository = personnelRepository;
    }

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String fileName,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        TokenValidationResponse token = authService.validateToken(authHeader);
        UUID farmId = token.getFarmId();
        if (farmId == null || !isOwnedByFarm(fileName, farmId)) {
            throw new AccessDeniedException("Access denied");
        }

        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();

            // Defense in depth: reject any attempt to escape the upload directory
            // (e.g. "../../etc/passwd" or URL-encoded variants).
            if (!filePath.startsWith(this.fileStorageLocation)) {
                log.warn("Rejected path traversal attempt: {}", fileName);
                return ResponseEntity.badRequest().build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                log.info("Receipt file not found: {}", fileName);
                return ResponseEntity.notFound().build();
            }

            String lower = fileName.toLowerCase();
            String contentType;
            if (lower.endsWith(".pdf")) {
                contentType = "application/pdf";
            } else if (lower.endsWith(".png")) {
                contentType = "image/png";
            } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
                contentType = "image/jpeg";
            } else if (lower.endsWith(".docx")) {
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            } else {
                contentType = "application/octet-stream";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception ex) {
            log.error("Failed to serve file {}: {}", fileName, ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    private boolean isOwnedByFarm(String fileName, UUID farmId) {
        return incomeRepository.existsByFarmIdAndReceipt(farmId, fileName)
                || expenseRepository.existsByFarmIdAndReceipt(farmId, fileName)
                || personnelRepository.existsByFarmIdAndProfilePicturePath(farmId, fileName)
                || personnelRepository.existsByFarmIdAndProfilePictureThumbnailPath(farmId, fileName);
    }
}
