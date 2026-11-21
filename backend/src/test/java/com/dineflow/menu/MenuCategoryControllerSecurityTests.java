package com.dineflow.menu;

import com.dineflow.common.api.ApiResponse;
import com.dineflow.menu.controller.MenuCategoryController;
import com.dineflow.menu.dto.CategoryResponse;
import com.dineflow.menu.dto.CategoryStatusUpdateRequest;
import com.dineflow.menu.dto.CreateCategoryRequest;
import com.dineflow.menu.dto.UpdateCategoryRequest;
import com.dineflow.menu.service.MenuCategoryService;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MenuCategoryControllerSecurityTests {

    @Autowired
    private MenuCategoryController categoryController;

    @Autowired
    private MenuCategoryService categoryService;

    private UUID tenantId;
    private UUID staffUserId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        staffUserId = UUID.randomUUID();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void authenticateWith(List<String> permissions, String roleCode, String type) {
        DineFlowPrincipal principal = new DineFlowPrincipal(
                staffUserId,
                tenantId,
                roleCode,
                permissions,
                type
        );
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("Staff with MENU_VIEW can list categories")
    void testMenuViewCanList() {
        authenticateWith(List.of("MENU_VIEW"), "WAITER", "STAFF");

        ResponseEntity<ApiResponse<List<CategoryResponse>>> response = categoryController.listAllCategories(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().success());
    }

    @Test
    @DisplayName("Staff with only MENU_VIEW cannot create category (AccessDeniedException)")
    void testMenuViewCannotCreateCategory() {
        authenticateWith(List.of("MENU_VIEW"), "WAITER", "STAFF");
        CreateCategoryRequest req = new CreateCategoryRequest("Desserts", "Sweet treats", 0, true, null, null);

        assertThrows(AccessDeniedException.class, () -> categoryController.createCategory(req));
    }

    @Test
    @DisplayName("Staff with MENU_MANAGE can create category")
    void testMenuManageCanCreateCategory() {
        authenticateWith(List.of("MENU_MANAGE", "MENU_VIEW"), "HOTEL_OWNER", "STAFF");
        CreateCategoryRequest req = new CreateCategoryRequest("Main Course", "Delicious main dishes", 1, true, null, null);

        ResponseEntity<ApiResponse<CategoryResponse>> response = categoryController.createCategory(req);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().success());
        assertEquals("Main Course", response.getBody().data().name());
    }

    @Test
    @DisplayName("Staff with only MENU_VIEW cannot update category (AccessDeniedException)")
    void testMenuViewCannotUpdateCategory() {
        authenticateWith(List.of("MENU_MANAGE", "MENU_VIEW"), "HOTEL_OWNER", "STAFF");
        CreateCategoryRequest req = new CreateCategoryRequest("Beverages", null, 0, true, null, null);
        CategoryResponse created = categoryService.createCategory(req);

        // Switch to a user with only MENU_VIEW
        authenticateWith(List.of("MENU_VIEW"), "WAITER", "STAFF");
        UpdateCategoryRequest updateReq = new UpdateCategoryRequest("Cold Drinks", null, 0, true, null, null);

        assertThrows(AccessDeniedException.class, () -> categoryController.updateCategory(created.id(), updateReq));
    }

    @Test
    @DisplayName("Guest customer session without MENU_MANAGE cannot access admin category endpoints")
    void testGuestCustomerCannotManageCategories() {
        authenticateWith(List.of("CUSTOMER_MENU_VIEW"), "CUSTOMER_GUEST", "GUEST");
        CreateCategoryRequest req = new CreateCategoryRequest("Hacked Category", null, 0, true, null, null);

        assertThrows(AccessDeniedException.class, () -> categoryController.createCategory(req));
    }
}
