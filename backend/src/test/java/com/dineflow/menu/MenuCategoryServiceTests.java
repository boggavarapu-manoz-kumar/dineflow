package com.dineflow.menu;

import com.dineflow.common.exception.BusinessException;
import com.dineflow.common.exception.ResourceNotFoundException;
import com.dineflow.menu.domain.MenuCategory;
import com.dineflow.menu.dto.CategoryReorderRequest;
import com.dineflow.menu.dto.CategoryResponse;
import com.dineflow.menu.dto.CategoryStatusUpdateRequest;
import com.dineflow.menu.dto.CreateCategoryRequest;
import com.dineflow.menu.dto.UpdateCategoryRequest;
import com.dineflow.menu.repository.MenuCategoryRepository;
import com.dineflow.menu.service.MenuCategoryService;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MenuCategoryServiceTests {

    @Autowired
    private MenuCategoryService categoryService;

    @Autowired
    private MenuCategoryRepository categoryRepository;

    private UUID tenantA;
    private UUID tenantB;
    private UUID staffAUserId;

    @BeforeEach
    void setUp() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        staffAUserId = UUID.randomUUID();

        // Establish tenant A context by default
        TenantContext.setTenantId(tenantA);

        // Setup security context with MENU_MANAGE and MENU_VIEW
        DineFlowPrincipal principal = new DineFlowPrincipal(
                staffAUserId,
                tenantA,
                "HOTEL_OWNER",
                List.of("MENU_MANAGE", "MENU_VIEW"),
                "STAFF"
        );
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Successfully create category with valid data")
    void testCreateCategorySuccess() {
        CreateCategoryRequest req = new CreateCategoryRequest(
                "Appetizers",
                "Starters & Finger food",
                1,
                true,
                "utensils",
                "http://example.com/appetizers.jpg"
        );

        CategoryResponse resp = categoryService.createCategory(req);

        assertNotNull(resp.id());
        assertEquals("Appetizers", resp.name());
        assertEquals(tenantA, resp.tenantId());
        assertEquals(1, resp.displayOrder());
        assertTrue(resp.isActive());
        assertEquals("utensils", resp.iconName());
    }

    @Test
    @DisplayName("Duplicate category name within same tenant must be rejected")
    void testDuplicateCategoryNameRejection() {
        CreateCategoryRequest req1 = new CreateCategoryRequest("Beverages", null, 0, true, null, null);
        categoryService.createCategory(req1);

        CreateCategoryRequest req2 = new CreateCategoryRequest("  beverages  ", null, 1, true, null, null);

        BusinessException ex = assertThrows(BusinessException.class, () -> categoryService.createCategory(req2));
        assertEquals("DUPLICATE_CATEGORY_NAME", ex.getErrorCode());
    }

    @Test
    @DisplayName("Different tenants can have identical category names")
    void testDifferentTenantsIdenticalNamesAllowed() {
        CreateCategoryRequest req = new CreateCategoryRequest("Desserts", "Sweet treats", 0, true, null, null);
        CategoryResponse respA = categoryService.createCategory(req);
        assertEquals(tenantA, respA.tenantId());

        // Switch to tenant B
        TenantContext.setTenantId(tenantB);
        CategoryResponse respB = categoryService.createCategory(req);
        assertEquals(tenantB, respB.tenantId());
        assertNotNull(respB.id());
    }

    @Test
    @DisplayName("Update category details and normalized name")
    void testUpdateCategory() {
        CreateCategoryRequest createReq = new CreateCategoryRequest("Main Courses", "Dinner mains", 0, true, null, null);
        CategoryResponse created = categoryService.createCategory(createReq);

        UpdateCategoryRequest updateReq = new UpdateCategoryRequest(
                "Chef Specials",
                "Signature hot dishes",
                5,
                true,
                "star",
                "http://example.com/specials.jpg"
        );

        CategoryResponse updated = categoryService.updateCategory(created.id(), updateReq);
        assertEquals("Chef Specials", updated.name());
        assertEquals(5, updated.displayOrder());
        assertEquals("star", updated.iconName());
    }

    @Test
    @DisplayName("Update category status toggles active flag")
    void testUpdateCategoryStatus() {
        CreateCategoryRequest createReq = new CreateCategoryRequest("Soups", "Warm broths", 0, true, null, null);
        CategoryResponse created = categoryService.createCategory(createReq);
        assertTrue(created.isActive());

        CategoryResponse deactivated = categoryService.updateCategoryStatus(created.id(), false);
        assertFalse(deactivated.isActive());

        CategoryResponse reactivated = categoryService.updateCategoryStatus(created.id(), true);
        assertTrue(reactivated.isActive());
    }

    @Test
    @DisplayName("Reorder categories updates sequential display orders")
    void testReorderCategories() {
        CategoryResponse c1 = categoryService.createCategory(new CreateCategoryRequest("Category 1", null, 10, true, null, null));
        CategoryResponse c2 = categoryService.createCategory(new CreateCategoryRequest("Category 2", null, 20, true, null, null));
        CategoryResponse c3 = categoryService.createCategory(new CreateCategoryRequest("Category 3", null, 30, true, null, null));

        // Invert order: C3, C1, C2
        CategoryReorderRequest reorderReq = new CategoryReorderRequest(List.of(c3.id(), c1.id(), c2.id()));
        List<CategoryResponse> reordered = categoryService.reorderCategories(reorderReq);

        assertEquals(3, reordered.size());
        assertEquals(c3.id(), reordered.get(0).id());
        assertEquals(0, reordered.get(0).displayOrder());

        assertEquals(c1.id(), reordered.get(1).id());
        assertEquals(1, reordered.get(1).displayOrder());

        assertEquals(c2.id(), reordered.get(2).id());
        assertEquals(2, reordered.get(2).displayOrder());
    }

    @Test
    @DisplayName("Cross-tenant access rejection: Tenant B cannot access or update Tenant A category")
    void testCrossTenantIsolation() {
        CreateCategoryRequest req = new CreateCategoryRequest("Secret Menu A", null, 0, true, null, null);
        CategoryResponse catA = categoryService.createCategory(req);

        // Switch to tenant B context
        TenantContext.setTenantId(tenantB);

        // Reading Tenant A category from Tenant B context must throw ResourceNotFoundException
        assertThrows(ResourceNotFoundException.class, () -> categoryService.getCategoryById(catA.id()));

        // Updating Tenant A category from Tenant B context must throw ResourceNotFoundException
        UpdateCategoryRequest updateReq = new UpdateCategoryRequest("Hacked Menu", null, 0, true, null, null);
        assertThrows(ResourceNotFoundException.class, () -> categoryService.updateCategory(catA.id(), updateReq));

        // Deleting Tenant A category from Tenant B context must throw ResourceNotFoundException
        assertThrows(ResourceNotFoundException.class, () -> categoryService.deleteCategory(catA.id()));
    }

    @Test
    @DisplayName("Delete category removes entity from tenant repository")
    void testDeleteCategory() {
        CreateCategoryRequest req = new CreateCategoryRequest("Seasonal Limited", null, 0, true, null, null);
        CategoryResponse created = categoryService.createCategory(req);

        categoryService.deleteCategory(created.id());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.getCategoryById(created.id()));
    }
}
