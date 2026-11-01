package com.agriva.service;

import com.agriva.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    private static final Tika TIKA = new Tika();

    /** 8 KB — enough for DOCX (ZIP + [Content_Types].xml) magic detection. */
    private static final int TIKA_HEAD_BYTES = 8192;

    private final Path root;
    private volatile boolean ready = false;
    private final long maxSizeBytes;
    private final Set<String> allowedMimeTypes;

    public FileStorageService(
            @Value("${agriva.uploads.dir}") String uploadDir,
            @Value("${agriva.uploads.max-size-bytes:10485760}") long maxSizeBytes,
            @Value("${agriva.uploads.allowed-mime-types:"
                    + "application/pdf,image/png,image/jpeg,"
                    + "application/vnd.openxmlformats-officedocument.wordprocessingml.document}")
            String allowedMimes) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.maxSizeBytes = maxSizeBytes;
        this.allowedMimeTypes = Set.copyOf(
                Arrays.stream(allowedMimes.split(",")).map(String::trim).toList());
        log.info("FileStorageService root={} maxSize={}B allowed={}",
                this.root, maxSizeBytes, this.allowedMimeTypes);
        try { Files.createDirectories(this.root); this.ready = true; }
        catch (IOException e) {
            log.warn("Uploads dir not ready at startup: {}", e.getMessage());
        }
    }

    public String storeFile(MultipartFile file) {
        ensureReady();

        if (file == null || file.isEmpty()) {
            throw new BusinessException("Empty file");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new BusinessException("File exceeds maximum size of " + maxSizeBytes + " bytes");
        }

        // Read 8 KB (not 2 KB) so DOCX detection is reliable.
        byte[] head;
        try (var is = file.getInputStream()) {
            head = is.readNBytes(TIKA_HEAD_BYTES);
        } catch (IOException e) {
            throw new BusinessException("Could not read uploaded file", e);
        }
        String detected;
        try { detected = TIKA.detect(head); }
        catch (Exception e) { detected = "application/octet-stream"; }

        if (!allowedMimeTypes.contains(detected)) {
            log.warn("Rejected upload: declared={} detected={}", file.getContentType(), detected);
            throw new BusinessException("File type not allowed (detected: " + detected + ")");
        }

        String ext = extensionFor(detected);
        String filename = UUID.randomUUID() + ext;

        try {
            Path target = this.root.resolve(filename).normalize();
            if (!target.startsWith(this.root)) {
                throw new BusinessException("Invalid storage path");
            }
            try (var is = file.getInputStream()) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Stored {} ({} bytes, type={}) -> {}",
                    file.getOriginalFilename(), file.getSize(), detected, filename);
            return filename;
        } catch (IOException e) {
            throw new BusinessException("Failed to store file: " + e.getMessage(), e);
        }
    }

    public void delete(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) return;
        try {
            Path p = this.root.resolve(storedFileName).normalize();
            if (!p.startsWith(this.root)) {
                log.warn("Rejected path traversal on delete: {}", storedFileName);
                throw new BusinessException("Invalid file path");
            }
            if (Files.exists(p)) {
                Files.delete(p);
                log.info("Deleted file {}", storedFileName);
            }
        } catch (IOException e) {
            throw new BusinessException("Could not delete file: " + e.getMessage(), e);
        }
    }

    private void ensureReady() {
        if (ready) return;
        try {
            Files.createDirectories(root);
            if (!Files.isWritable(root)) {
                throw new BusinessException("Uploads directory not writable: " + root);
            }
            ready = true;
        } catch (IOException e) {
            throw new BusinessException("Uploads directory not accessible: " + root, e);
        }
    }

    private static String extensionFor(String mime) {
        return switch (mime) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            default -> "";
        };
    }
}