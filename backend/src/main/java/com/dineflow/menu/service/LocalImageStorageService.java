package com.dineflow.menu.service;

import com.dineflow.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalImageStorageService implements ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalImageStorageService.class);
    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final Path rootStoragePath = Paths.get("uploads", "images");

    public LocalImageStorageService() {
        try {
            Files.createDirectories(rootStoragePath);
        } catch (IOException e) {
            log.error("Could not initialize local image storage folder", e);
        }
    }

    @Override
    public StoredImageResult storeImage(UUID tenantId, UUID entityId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Image file is missing or empty", HttpStatus.BAD_REQUEST, "INVALID_IMAGE_FILE");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException("Image file size exceeds maximum limit of 5MB", HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException("Only JPEG, PNG, and WebP images are permitted", HttpStatus.BAD_REQUEST, "INVALID_IMAGE_TYPE");
        }

        // Magic byte validation to prevent renamed executable/malicious uploads
        validateMagicBytes(file, contentType);

        String extension = switch (contentType.toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".img";
        };

        String generatedFileName = UUID.randomUUID() + extension;
        String storageKey = tenantId + "/" + entityId + "/" + generatedFileName;
        Path targetDir = rootStoragePath.resolve(tenantId.toString()).resolve(entityId.toString());
        Path targetPath = targetDir.resolve(generatedFileName);

        try {
            Files.createDirectories(targetDir);
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Failed to persist file to disk: {}", targetPath, e);
            throw new BusinessException("Failed to store image file", HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR");
        }

        String imageUrl = "/api/v1/public/images/" + storageKey;
        return new StoredImageResult(imageUrl, storageKey, file.getOriginalFilename(), file.getSize(), contentType);
    }

    @Override
    public void deleteImage(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            Path targetPath = rootStoragePath.resolve(storageKey).normalize();
            if (!targetPath.startsWith(rootStoragePath)) {
                log.warn("Path traversal detected while attempting to delete image: {}", storageKey);
                return;
            }
            Files.deleteIfExists(targetPath);
            log.info("Deleted image from storage: {}", storageKey);
        } catch (IOException e) {
            log.warn("Failed to delete image: {}", storageKey, e);
        }
    }

    @Override
    public Optional<org.springframework.core.io.Resource> loadAsResource(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return Optional.empty();
        }
        try {
            Path targetPath = rootStoragePath.resolve(storageKey).normalize();
            // Path traversal protection
            if (!targetPath.startsWith(rootStoragePath)) {
                log.warn("Path traversal attempt detected in loadAsResource: {}", storageKey);
                return Optional.empty();
            }
            if (!Files.exists(targetPath) || !Files.isReadable(targetPath)) {
                return Optional.empty();
            }
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(targetPath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return Optional.of(resource);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Could not load image resource for key: {}", storageKey, e);
            return Optional.empty();
        }
    }

    private void validateMagicBytes(MultipartFile file, String contentType) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (read < 4) {
                throw new BusinessException("Corrupted image header", HttpStatus.BAD_REQUEST, "INVALID_IMAGE_HEADER");
            }

            boolean valid = false;
            if (contentType.equalsIgnoreCase("image/jpeg")) {
                // JPEG magic bytes: FF D8 FF
                valid = (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF);
            } else if (contentType.equalsIgnoreCase("image/png")) {
                // PNG magic bytes: 89 50 4E 47
                valid = (header[0] == (byte) 0x89 && header[1] == (byte) 0x50 && header[2] == (byte) 0x4E && header[3] == (byte) 0x47);
            } else if (contentType.equalsIgnoreCase("image/webp")) {
                // WEBP magic bytes: RIFF....WEBP
                valid = (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                        && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P');
            }

            if (!valid) {
                throw new BusinessException("File content does not match reported image MIME type", HttpStatus.BAD_REQUEST, "IMAGE_SIGNATURE_MISMATCH");
            }
        } catch (IOException e) {
            throw new BusinessException("Unable to inspect image header", HttpStatus.BAD_REQUEST, "INVALID_IMAGE_STREAM");
        }
    }
}
