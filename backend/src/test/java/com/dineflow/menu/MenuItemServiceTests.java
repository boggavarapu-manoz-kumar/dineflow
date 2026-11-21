package com.dineflow.menu;

import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.common.exception.ResourceNotFoundException;
import com.dineflow.menu.domain.DietaryClassification;
import com.dineflow.menu.domain.MenuCategory;
import com.dineflow.menu.domain.MenuItem;
import com.dineflow.menu.dto.BranchConfigOverrideRequest;
import com.dineflow.menu.dto.BranchConfigResponse;
import com.dineflow.menu.dto.CreateCategoryRequest;
import com.dineflow.menu.dto.CreateMenuItemRequest;
import com.dineflow.menu.dto.CreateOptionGroupRequest;
import com.dineflow.menu.dto.CreateOptionRequest;
import com.dineflow.menu.dto.CreateVariantRequest;
import com.dineflow.menu.dto.MenuItemDetailResponse;
import com.dineflow.menu.dto.UpdateMenuItemRequest;
import com.dineflow.menu.repository.MenuCategoryRepository;
import com.dineflow.menu.repository.MenuItemRepository;
import com.dineflow.menu.service.MenuCategoryService;
import com.dineflow.menu.service.MenuItemService;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
class MenuItemServiceTests {

    @Autowired
    private MenuItemService menuItemService;

    @Autowired
    private MenuCategoryService categoryService;

    @Autowired
    private MenuCategoryRepository categoryRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private BranchRepository branchRepository;

    private UUID tenantA;
    private UUID tenantB;
    private UUID staffAUserId;
    private UUID categoryAId;
    private Branch branchA;

    @BeforeEach
    void setUp() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        staffAUserId = UUID.randomUUID();

        TenantContext.setTenantId(tenantA);

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

        // Setup base category for Tenant A
        var catResp = categoryService.createCategory(new CreateCategoryRequest(
                "Pizzas & Pastas", "Authentic Italian", 0, true, "pizza", null
        ));
        categoryAId = catResp.id();

        // Setup Branch for Tenant A
        Branch b = new Branch("Downtown Branch", "DT-01", "123 Main St", "+919876543210", true, true);
        b.setTenantId(tenantA);
        branchA = branchRepository.save(b);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Create food item with valid fields and dietary tags")
    void testCreateMenuItemSuccess() {
        CreateMenuItemRequest req = new CreateMenuItemRequest(
                categoryAId,
                "Margherita Pizza",
                "Classic cheese and fresh basil",
                "Stone-baked sourdough crust topped with San Marzano tomato sauce and fresh buffalo mozzarella",
                new BigDecimal("350.00"),
                DietaryClassification.VEGETARIAN,
                20,
                true,
                true,
                1
        );

        MenuItemDetailResponse item = menuItemService.createMenuItem(req);

        assertNotNull(item.id());
        assertEquals("Margherita Pizza", item.name());
        assertEquals(new BigDecimal("350.00"), item.basePrice());
        assertEquals(DietaryClassification.VEGETARIAN, item.dietaryClassification());
        assertEquals(categoryAId, item.categoryId());
        assertEquals("Pizzas & Pastas", item.categoryName());
    }

    @Test
    @DisplayName("Duplicate food item name within same tenant must be rejected")
    void testDuplicateFoodItemNameRejection() {
        CreateMenuItemRequest req1 = new CreateMenuItemRequest(
                categoryAId, "Pepperoni Pizza", null, null, new BigDecimal("450.00"),
                DietaryClassification.NON_VEGETARIAN, 15, true, true, 0
        );
        menuItemService.createMenuItem(req1);

        CreateMenuItemRequest req2 = new CreateMenuItemRequest(
                categoryAId, "  pepperoni pizza  ", null, null, new BigDecimal("480.00"),
                DietaryClassification.NON_VEGETARIAN, 15, true, true, 1
        );

        BusinessException ex = assertThrows(BusinessException.class, () -> menuItemService.createMenuItem(req2));
        assertEquals("DUPLICATE_ITEM_NAME", ex.getErrorCode());
    }

    @Test
    @DisplayName("Prevent assigning item to a category belonging to another tenant")
    void testPreventCrossTenantCategoryAssignment() {
        // Switch to Tenant B and create category
        TenantContext.setTenantId(tenantB);
        var catB = categoryService.createCategory(new CreateCategoryRequest(
                "Tenant B Category", null, 0, true, null, null
        ));

        // Switch back to Tenant A
        TenantContext.setTenantId(tenantA);

        CreateMenuItemRequest req = new CreateMenuItemRequest(
                catB.id(), "Stolen Category Pizza", null, null, new BigDecimal("299.00"),
                DietaryClassification.VEGETARIAN, 10, true, true, 0
        );

        assertThrows(ResourceNotFoundException.class, () -> menuItemService.createMenuItem(req));
    }

    @Test
    @DisplayName("Add variants with price overrides to food item")
    void testAddVariantsToItem() {
        CreateMenuItemRequest req = new CreateMenuItemRequest(
                categoryAId, "Farmhouse Pizza", null, null, new BigDecimal("300.00"),
                DietaryClassification.VEGETARIAN, 15, true, true, 0
        );
        MenuItemDetailResponse item = menuItemService.createMenuItem(req);

        var small = menuItemService.addVariant(item.id(), new CreateVariantRequest(
                "Small (7\")", "Serves 1", new BigDecimal("249.00"), 0, true, true
        ));
        var large = menuItemService.addVariant(item.id(), new CreateVariantRequest(
                "Large (12\")", "Serves 3-4", new BigDecimal("499.00"), 1, true, true
        ));

        assertNotNull(small.id());
        assertEquals(new BigDecimal("249.00"), small.price());
        assertNotNull(large.id());
        assertEquals(new BigDecimal("499.00"), large.price());

        MenuItemDetailResponse fetched = menuItemService.getMenuItemById(item.id());
        assertEquals(2, fetched.variants().size());
    }

    @Test
    @DisplayName("Option groups and add-ons configuration with validation")
    void testOptionGroupsAndAddOns() {
        CreateOptionGroupRequest groupReq = new CreateOptionGroupRequest(
                "Select Crust", "Choice of crust", 1, 1, true, 0, true
        );
        var group = menuItemService.createOptionGroup(groupReq);
        assertEquals(1, group.minSelections());
        assertEquals(1, group.maxSelections());
        assertTrue(group.isRequired());

        var opt1 = menuItemService.addOptionToGroup(group.id(), new CreateOptionRequest(
                "Pan Crust", "Regular crust", BigDecimal.ZERO, 0, true, true
        ));
        var opt2 = menuItemService.addOptionToGroup(group.id(), new CreateOptionRequest(
                "Cheese Burst", "Filled with liquid cheese", new BigDecimal("99.00"), 1, true, true
        ));

        assertEquals(BigDecimal.ZERO, opt1.additionalPrice());
        assertEquals(new BigDecimal("99.00"), opt2.additionalPrice());

        // Link group to item
        CreateMenuItemRequest itemReq = new CreateMenuItemRequest(
                categoryAId, "Cheese Pizza", null, null, new BigDecimal("299.00"),
                DietaryClassification.VEGETARIAN, 15, true, true, 0
        );
        MenuItemDetailResponse item = menuItemService.createMenuItem(itemReq);
        menuItemService.linkOptionGroupToItem(item.id(), group.id());

        MenuItemDetailResponse itemWithGroups = menuItemService.getMenuItemById(item.id());
        assertEquals(1, itemWithGroups.optionGroups().size());
    }

    @Test
    @DisplayName("Branch-level price override and stock availability")
    void testBranchSpecificPriceAndAvailability() {
        CreateMenuItemRequest itemReq = new CreateMenuItemRequest(
                categoryAId, "Truffle Pasta", null, null, new BigDecimal("500.00"),
                DietaryClassification.VEGAN, 25, true, true, 0
        );
        MenuItemDetailResponse item = menuItemService.createMenuItem(itemReq);

        // Override at Downtown Branch: price 550.00, out of stock (available=false)
        BranchConfigOverrideRequest overrideReq = new BranchConfigOverrideRequest(
                new BigDecimal("550.00"), false, true
        );
        BranchConfigResponse config = menuItemService.setBranchOverride(branchA.getId(), item.id(), overrideReq);

        assertEquals(new BigDecimal("550.00"), config.priceOverride());
        assertFalse(config.isAvailable());
        assertTrue(config.isVisible());
    }

    @Test
    @DisplayName("Category cannot be deleted while containing menu items")
    void testCategoryDeletionBlockedWhenItemsPresent() {
        CreateMenuItemRequest itemReq = new CreateMenuItemRequest(
                categoryAId, "Spaghetti Carbonara", null, null, new BigDecimal("420.00"),
                DietaryClassification.NON_VEGETARIAN, 20, true, true, 0
        );
        menuItemService.createMenuItem(itemReq);

        BusinessException ex = assertThrows(BusinessException.class, () -> categoryService.deleteCategory(categoryAId));
        assertEquals("CATEGORY_HAS_ITEMS", ex.getErrorCode());
    }

    @Test
    @DisplayName("Safe image upload with magic bytes verification and size checks")
    void testImageUploadValidation() {
        CreateMenuItemRequest itemReq = new CreateMenuItemRequest(
                categoryAId, "Garlic Bread", null, null, new BigDecimal("150.00"),
                DietaryClassification.VEGETARIAN, 10, true, true, 0
        );
        MenuItemDetailResponse item = menuItemService.createMenuItem(itemReq);

        // Valid PNG with proper header bytes
        byte[] validPngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile validFile = new MockMultipartFile(
                "file", "bread.png", "image/png", validPngBytes
        );

        var imgResp = menuItemService.uploadItemImage(item.id(), validFile, true);
        assertNotNull(imgResp.id());
        assertTrue(imgResp.isPrimary());
        assertTrue(imgResp.imageUrl().contains("/api/v1/public/images/"));

        // Invalid fake image (fraudulent MIME type)
        byte[] fakeBytes = "Not an image, plain text file".getBytes();
        MockMultipartFile fakeFile = new MockMultipartFile(
                "file", "malicious.png", "image/png", fakeBytes
        );

        BusinessException ex = assertThrows(BusinessException.class, () ->
                menuItemService.uploadItemImage(item.id(), fakeFile, false));
        assertEquals("IMAGE_SIGNATURE_MISMATCH", ex.getErrorCode());
    }

    @Test
    @DisplayName("Cross-tenant item access and manipulation is rejected")
    void testCrossTenantItemAccess() {
        CreateMenuItemRequest itemReq = new CreateMenuItemRequest(
                categoryAId, "Tenant A Lasagna", null, null, new BigDecimal("400.00"),
                DietaryClassification.VEGETARIAN, 20, true, true, 0
        );
        MenuItemDetailResponse itemA = menuItemService.createMenuItem(itemReq);

        // Switch to Tenant B
        TenantContext.setTenantId(tenantB);
        DineFlowPrincipal principalB = new DineFlowPrincipal(
                UUID.randomUUID(),
                tenantB,
                "HOTEL_OWNER",
                List.of("MENU_MANAGE", "MENU_VIEW"),
                "STAFF"
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principalB, null, principalB.getAuthorities())
        );

        assertThrows(ResourceNotFoundException.class, () -> menuItemService.getMenuItemById(itemA.id()));

        UpdateMenuItemRequest updateReq = new UpdateMenuItemRequest(
                categoryAId, "Hacked Lasagna", null, null, new BigDecimal("10.00"),
                DietaryClassification.VEGETARIAN, 5, true, true, 0
        );
        assertThrows(ResourceNotFoundException.class, () -> menuItemService.updateMenuItem(itemA.id(), updateReq));

        assertThrows(ResourceNotFoundException.class, () -> menuItemService.deleteMenuItem(itemA.id()));
    }
}
