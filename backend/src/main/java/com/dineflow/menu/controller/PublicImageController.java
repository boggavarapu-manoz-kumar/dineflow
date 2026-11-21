package com.dineflow.menu.controller;

import com.dineflow.common.exception.ResourceNotFoundException;
import com.dineflow.menu.domain.MenuItemImage;
import com.dineflow.menu.repository.MenuItemImageRepository;
import com.dineflow.menu.service.ImageStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/public/images")
public class PublicImageController {

    private final ImageStorageService imageStorageService;
    private final MenuItemImageRepository menuItemImageRepository;

    public PublicImageController(ImageStorageService imageStorageService,
                                 MenuItemImageRepository menuItemImageRepository) {
        this.imageStorageService = imageStorageService;
        this.menuItemImageRepository = menuItemImageRepository;
    }

    @GetMapping("/{tenantId}/{entityId}/{fileName}")
    public ResponseEntity<Resource> getPublicImage(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("entityId") UUID entityId,
            @PathVariable("fileName") String fileName) {

        // Validate basic path components and file name format to prevent path traversal
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        String storageKey = tenantId + "/" + entityId + "/" + fileName;

        // Verify storageKey belongs to an approved menu image record for this tenant and entity directly via DB query
        Optional<MenuItemImage> imageRecordOpt = menuItemImageRepository.findByStorageKeyAndTenantId(storageKey, tenantId);

        if (imageRecordOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        MenuItemImage imageRecord = imageRecordOpt.get();

        if (imageRecord.getMenuItem() == null || !imageRecord.getMenuItem().getId().equals(entityId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // Ensure menu item is active
        if (!Boolean.TRUE.equals(imageRecord.getMenuItem().getIsActive())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Optional<Resource> resourceOpt = imageStorageService.loadAsResource(storageKey);
        if (resourceOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        Resource resource = resourceOpt.get();
        String contentType = imageRecord.getContentType();
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }
}
