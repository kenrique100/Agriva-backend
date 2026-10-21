package com.agriva.service;

import com.agriva.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    private final Path fileStorageLocation;
    private volatile boolean directoryReady = false;

    public FileStorageService(@Value("${agriva.uploads.dir}") String uploadDir) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        log.info("FileStorageService resolved uploads directory: {}", this.fileStorageLocation);

        // Do NOT throw on failure. Startup must succeed even if the uploads dir
        // cannot be created yet — otherwise a single permission glitch brings down
        // the entire app (auth, incomes, expenses list, everything).
        // ensureDirectory() will retry on the first actual upload.
        try {
            Files.createDirectories(this.fileStorageLocation);
            this.directoryReady = true;
            log.info("Uploads directory is ready: {}", this.fileStorageLocation);
        } catch (IOException ex) {
            log.warn(
                    "Could not create uploads directory {} at startup: {}. "
                            + "The application will start, but receipt uploads will fail "
                            + "until the directory is writable. In Docker, ensure the volume "
                            + "is chowned to UID 1001 (see docker-compose.yml agriva-init service).",
                    this.fileStorageLocation, ex.getMessage());
        }
    }

    private synchronized void ensureDirectory() {
        if (directoryReady) return;
        try {
            Files.createDirectories(this.fileStorageLocation);
            if (!Files.isWritable(this.fileStorageLocation)) {
                throw new BusinessException(
                        "Uploads directory is not writable: " + this.fileStorageLocation);
            }
            directoryReady = true;
            log.info("Uploads directory became ready: {}", this.fileStorageLocation);
        } catch (IOException ex) {
            throw new BusinessException(
                    "Uploads directory is not accessible: " + this.fileStorageLocation
                            + " (" + ex.getMessage() + ")");
        }
    }

    public String storeFile(MultipartFile file) {
        ensureDirectory();

        String originalFileName = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename());

        String extension = "";
        int i = originalFileName.lastIndexOf('.');
        if (i > 0) {
            extension = originalFileName.substring(i).toLowerCase();
        }

        String allowedExtensions = ".pdf,.docx,.png,.jpeg,.jpg";
        if (!allowedExtensions.contains(extension)) {
            throw new BusinessException(
                    "Invalid file type. Only PDF, DOCX, PNG, JPEG are allowed.");
        }

        String newFileName = UUID.randomUUID() + extension;

        try {
            if (newFileName.contains("..")) {
                throw new BusinessException(
                        "Sorry! Filename contains invalid path sequence " + newFileName);
            }
            Path targetLocation = this.fileStorageLocation.resolve(newFileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            log.info("Stored receipt: {} -> {}", originalFileName, newFileName);
            return newFileName;
        } catch (IOException ex) {
            throw new BusinessException(
                    "Could not store file " + newFileName + ". Please try again!");
        }
    }

    /**
     * NEW: deletes a previously stored file by name (the UUID-based filename
     * that storeFile() returned). Missing files are silently ignored so that
     * re-uploads do not fail because a stale path no longer exists on disk.
     * Includes the same path-traversal guard as the GET endpoint in
     * FileController — a malicious stored value cannot escape the uploads dir.
     */
    public void delete(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) {
            return;
        }

        try {
            Path filePath = this.fileStorageLocation.resolve(storedFileName).normalize();

            if (!filePath.startsWith(this.fileStorageLocation)) {
                log.warn("Rejected path traversal on delete: {}", storedFileName);
                throw new BusinessException("Invalid file path: " + storedFileName);
            }

            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Deleted file: {}", storedFileName);
            } else {
                log.debug("Delete skipped — file not found: {}", storedFileName);
            }
        } catch (IOException ex) {
            log.error("Failed to delete file {}: {}", storedFileName, ex.getMessage());
            throw new BusinessException(
                    "Could not delete file " + storedFileName + ": " + ex.getMessage());
        }
    }
}