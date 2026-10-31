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
            if (!filePath.startsWith(this.fileStorageLocation)) {
                log.warn("Path traversal attempt: {}", fileName);
                return ResponseEntity.badRequest().build();
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String lower = fileName.toLowerCase();
            String contentType = lower.endsWith(".pdf")  ? "application/pdf"
                    : lower.endsWith(".png") ? "image/png"
                      : lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? "image/jpeg"
                        : lower.endsWith(".docx") ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                          : "application/octet-stream";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + resource.getFilename() + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
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