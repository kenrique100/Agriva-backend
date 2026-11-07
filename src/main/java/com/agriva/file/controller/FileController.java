package com.agriva.file.controller;

import com.agriva.shared.port.FileOwnershipPort;
import com.agriva.shared.security.AuthenticatedUser;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@Slf4j
public class FileController {

    private final Path fileStorageLocation;
    private final List<FileOwnershipPort> ownershipPorts;

    public FileController(
            @Value("${agriva.uploads.dir}") String uploadDir,
            List<FileOwnershipPort> ownershipPorts) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.ownershipPorts = ownershipPorts;
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
        for (FileOwnershipPort port : ownershipPorts) {
            if (port.isOwnedByFarm(fileName, farmId)) return true;
        }
        return false;
    }
}