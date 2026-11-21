package com.dineflow.menu;

import com.dineflow.menu.controller.PublicImageController;
import com.dineflow.menu.domain.DietaryClassification;
import com.dineflow.menu.domain.MenuCategory;
import com.dineflow.menu.domain.MenuItem;
import com.dineflow.menu.domain.MenuItemImage;
import com.dineflow.menu.repository.MenuCategoryRepository;
import com.dineflow.menu.repository.MenuItemImageRepository;
import com.dineflow.menu.repository.MenuItemRepository;
import com.dineflow.menu.service.ImageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PublicImageDeliverySecurityTests {

    @Autowired
    private PublicImageController publicImageController;

    @Autowired
    private ImageStorageService imageStorageService;

    @Autowired
    private MenuCategoryRepository categoryRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private MenuItemImageRepository menuItemImageRepository;

    private UUID tenantId;
    private MenuItem activeItem;
    private MenuItem inactiveItem;
    private String storedFileName;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        MenuCategory category = new MenuCategory("Desserts", null, 0, true, null, null);
        category.setTenantId(tenantId);
        categoryRepository.save(category);

        activeItem = new MenuItem(category, "Tiramisu", "Classic dessert", null,
                new BigDecimal("180.00"), DietaryClassification.VEGETARIAN, 10, true, true, 0);
        activeItem.setTenantId(tenantId);
        menuItemRepository.save(activeItem);

        inactiveItem = new MenuItem(category, "Old Pudding", "Discontinued dessert", null,
                new BigDecimal("120.00"), DietaryClassification.VEGETARIAN, 10, false, false, 1);
        inactiveItem.setTenantId(tenantId);
        menuItemRepository.save(inactiveItem);

        // Store a test PNG file
        byte[] validPngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "dessert.png", "image/png", validPngHeader);
        ImageStorageService.StoredImageResult stored = imageStorageService.storeImage(tenantId, activeItem.getId(), file);

        String[] keyParts = stored.storageKey().split("/");
        storedFileName = keyParts[2];

        MenuItemImage imageEntity = new MenuItemImage(
                activeItem,
                stored.imageUrl(),
                stored.storageKey(),
                "dessert.png",
                stored.fileSizeBytes(),
                stored.contentType(),
                0,
                true
        );
        imageEntity.setTenantId(tenantId);
        menuItemImageRepository.save(imageEntity);
    }

    @Test
    @DisplayName("Public can fetch valid active menu item image with correct headers and nosniff")
    void testGetPublicImageSuccess() {
        ResponseEntity<Resource> response = publicImageController.getPublicImage(tenantId, activeItem.getId(), storedFileName);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("nosniff", response.getHeaders().getFirst("X-Content-Type-Options"));
        assertNotNull(response.getHeaders().getCacheControl());
    }

    @Test
    @DisplayName("Path traversal attempts with '..' or slashes are blocked with 400 Bad Request")
    void testPathTraversalBlocked() {
        ResponseEntity<Resource> response1 = publicImageController.getPublicImage(tenantId, activeItem.getId(), "../etc/passwd");
        assertEquals(HttpStatus.BAD_REQUEST, response1.getStatusCode());

        ResponseEntity<Resource> response2 = publicImageController.getPublicImage(tenantId, activeItem.getId(), "..\\windows\\system32");
        assertEquals(HttpStatus.BAD_REQUEST, response2.getStatusCode());
    }

    @Test
    @DisplayName("Non-existent image or unapproved file returns 404 Not Found")
    void testMissingImageNotFound() {
        ResponseEntity<Resource> response = publicImageController.getPublicImage(tenantId, activeItem.getId(), "non-existent-image.png");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("Image of inactive/discontinued menu item returns 404 Not Found")
    void testInactiveItemImageBlocked() {
        // Create image record linked to inactive item
        MenuItemImage inactiveImage = new MenuItemImage(
                inactiveItem,
                "/api/v1/public/images/" + tenantId + "/" + inactiveItem.getId() + "/" + storedFileName,
                tenantId + "/" + inactiveItem.getId() + "/" + storedFileName,
                "pudding.png",
                12L,
                "image/png",
                0,
                true
        );
        inactiveImage.setTenantId(tenantId);
        menuItemImageRepository.save(inactiveImage);

        ResponseEntity<Resource> response = publicImageController.getPublicImage(tenantId, inactiveItem.getId(), storedFileName);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
