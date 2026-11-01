package com.agriva.controller;

import com.agriva.repository.ExpenseRepository;
import com.agriva.repository.IncomeRepository;
import com.agriva.repository.PersonnelRepository;
import com.agriva.security.AuthenticatedUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@Slf4j
public class FileController {

    private final Path fileStorageLocation;
    private final IncomeRepository incomeRepo;
    private final ExpenseRepository expenseRepo;
    private final PersonnelRepository personnelRepo;

    public FileController(
            @Value("${agriva.uploads.dir}") String uploadDir,
            IncomeRepository incomeRepo,
            ExpenseRepository expenseRepo,
            PersonnelRepository personnelRepo) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.incomeRepo = incomeRepo;
        this.expenseRepo = expenseRepo;
        this.personnelRepo = personnelRepo;
    }

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String fileName,
            @AuthenticationPrincipal AuthenticatedUser principal) {

        UUID farmId = principal.farmId();
        if (farmId == null || !isOwnedByFarm(fileName, farmId)) {
            throw new AccessDeniedException("Access denied");
        }

        try {
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();

            // Do not differentiate "bad path" from "missing file" — same 404
            // in both branches so we don't leak filesystem state (audit C8).
            if (!filePath.startsWith(this.fileStorageLocation)) {
                log.warn("Path traversal attempt rejected: {}", fileName);
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String lower = fileName.toLowerCase();
            boolean isImage = lower.endsWith(".png")
                    || lower.endsWith(".jpg")
                    || lower.endsWith(".jpeg");

            String contentType = lower.endsWith(".pdf")   ? "application/pdf"
                    : lower.endsWith(".png")              ? "image/png"
                      : lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? "image/jpeg"
                        : lower.endsWith(".docx")             ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                          : "application/octet-stream";

            // Images must be inline (for <img> tags).
            // Everything else — PDFs especially — must be attachment, because
            // inline PDFs let an attacker upload a payload PDF and execute it
            // with the victim's session cookie (audit C9).
            String disposition = isImage
                    ? "inline; filename=\"" + resource.getFilename() + "\""
                    : "attachment; filename=\"" + resource.getFilename() + "\"";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(resource);
        } catch (Exception ex) {
            log.error("Failed to serve file {}: {}", fileName, ex.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    private boolean isOwnedByFarm(String fileName, UUID farmId) {
        return incomeRepo.existsByFarmIdAndReceipt(farmId, fileName)
                || expenseRepo.existsByFarmIdAndReceipt(farmId, fileName)
                || personnelRepo.existsByFarmIdAndProfilePicturePath(farmId, fileName)
                || personnelRepo.existsByFarmIdAndProfilePictureThumbnailPath(farmId, fileName);
    }
}