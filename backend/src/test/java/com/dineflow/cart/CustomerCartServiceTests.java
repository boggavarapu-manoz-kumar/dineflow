package com.dineflow.cart;

import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
import com.dineflow.cart.domain.CustomerCart;
import com.dineflow.cart.dto.AddCartItemRequest;
import com.dineflow.cart.dto.CartResponse;
import com.dineflow.cart.dto.UpdateCartItemQuantityRequest;
import com.dineflow.cart.repository.CustomerCartRepository;
import com.dineflow.cart.service.CustomerCartService;
import com.dineflow.common.exception.BusinessException;
import com.dineflow.identity.domain.CustomerSession;
import com.dineflow.identity.repository.CustomerSessionRepository;
import com.dineflow.menu.domain.DietaryClassification;
import com.dineflow.menu.domain.MenuCategory;
import com.dineflow.menu.domain.MenuItem;
import com.dineflow.menu.domain.MenuItemBranchConfiguration;
import com.dineflow.menu.domain.MenuOption;
import com.dineflow.menu.domain.MenuOptionGroup;
import com.dineflow.menu.domain.MenuVariant;
import com.dineflow.menu.repository.MenuCategoryRepository;
import com.dineflow.menu.repository.MenuItemBranchConfigurationRepository;
import com.dineflow.menu.repository.MenuItemRepository;
import com.dineflow.menu.repository.MenuOptionGroupRepository;
import com.dineflow.menu.repository.MenuOptionRepository;
import com.dineflow.menu.repository.MenuVariantRepository;
import com.dineflow.security.DineFlowPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CustomerCartServiceTests {

    @Autowired
    private CustomerCartService cartService;

    @Autowired
    private CustomerCartRepository cartRepository;

    @Autowired
    private CustomerSessionRepository sessionRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private MenuCategoryRepository categoryRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private MenuVariantRepository variantRepository;

    @Autowired
    private MenuOptionGroupRepository optionGroupRepository;

    @Autowired
    private MenuOptionRepository optionRepository;

    @Autowired
    private MenuItemBranchConfigurationRepository branchConfigRepository;

    private UUID tenantA;
    private UUID tenantB;
    private Branch branchA;
    private CustomerSession sessionCustomer1;
    private CustomerSession sessionCustomer2;
    private DineFlowPrincipal principalCustomer1;
    private DineFlowPrincipal principalCustomer2;

    private MenuItem burger;
    private MenuVariant largeBurgerVariant;
    private MenuOption extraCheese;
    private MenuOption bacon;

    @BeforeEach
    void setUp() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();

        branchA = new Branch("Main Branch", "MB-01", "100 High St", "+919876543210", true, true);
        branchA.setTenantId(tenantA);
        branchRepository.save(branchA);

        sessionCustomer1 = new CustomerSession();
        sessionCustomer1.setTenantId(tenantA);
        sessionCustomer1.setBranchId(branchA.getId());
        sessionCustomer1.setTableNumber("T-01");
        sessionCustomer1.setCustomerName("Guest One");
        sessionCustomer1.setMaskedMobile("+91****11");
        sessionCustomer1.setSessionTokenHash(UUID.randomUUID().toString());
        sessionCustomer1.setStatus("ACTIVE");
        sessionCustomer1.setExpiresAt(Instant.now().plusSeconds(3600));
        sessionRepository.save(sessionCustomer1);

        sessionCustomer2 = new CustomerSession();
        sessionCustomer2.setTenantId(tenantA);
        sessionCustomer2.setBranchId(branchA.getId());
        sessionCustomer2.setTableNumber("T-02");
        sessionCustomer2.setCustomerName("Guest Two");
        sessionCustomer2.setMaskedMobile("+91****22");
        sessionCustomer2.setSessionTokenHash(UUID.randomUUID().toString());
        sessionCustomer2.setStatus("ACTIVE");
        sessionCustomer2.setExpiresAt(Instant.now().plusSeconds(3600));
        sessionRepository.save(sessionCustomer2);

        principalCustomer1 = new DineFlowPrincipal(sessionCustomer1.getId(), tenantA, branchA.getId(), "CUSTOMER_GUEST", List.of("CUSTOMER_MENU_VIEW"), "GUEST");
        principalCustomer2 = new DineFlowPrincipal(sessionCustomer2.getId(), tenantA, branchA.getId(), "CUSTOMER_GUEST", List.of("CUSTOMER_MENU_VIEW"), "GUEST");

        MenuCategory cat = new MenuCategory("Mains", null, 0, true, null, null);
        cat.setTenantId(tenantA);
        categoryRepository.save(cat);

        burger = new MenuItem(cat, "Gourmet Burger", "Juicy craft burger", null, new BigDecimal("200.00"), DietaryClassification.NON_VEGETARIAN, 15, true, true, 0);
        burger.setTenantId(tenantA);
        menuItemRepository.save(burger);

        largeBurgerVariant = new MenuVariant(burger, "Large Double Patty", null, new BigDecimal("280.00"), 0, true, true);
        largeBurgerVariant.setTenantId(tenantA);
        variantRepository.save(largeBurgerVariant);

        MenuOptionGroup addOns = new MenuOptionGroup("Add-ons", null, 0, 3, false, 0, true);
        addOns.setTenantId(tenantA);
        optionGroupRepository.save(addOns);

        burger.getOptionGroups().add(addOns);
        menuItemRepository.save(burger);

        extraCheese = new MenuOption(addOns, "Extra Cheddar", null, new BigDecimal("30.00"), 0, true, true);
        extraCheese.setTenantId(tenantA);
        optionRepository.save(extraCheese);

        bacon = new MenuOption(addOns, "Crispy Bacon", null, new BigDecimal("50.00"), 1, true, true);
        bacon.setTenantId(tenantA);
        optionRepository.save(bacon);
    }

    @Test
    @DisplayName("Customer can initialize an empty cart scoped to their session")
    void testGetOrCreateCart() {
        CartResponse cart = cartService.getOrCreateCart(principalCustomer1);

        assertNotNull(cart.cartId());
        assertEquals(tenantA, cart.tenantId());
        assertEquals(branchA.getId(), cart.branchId());
        assertEquals("T-01", cart.tableNumber());
        assertEquals(0, cart.totalItemCount());
        assertEquals(BigDecimal.ZERO.setScale(2), cart.subtotal());
    }

    @Test
    @DisplayName("Adding standard item without variant calculates base item line total")
    void testAddStandardItem() {
        AddCartItemRequest request = new AddCartItemRequest(burger.getId(), null, List.of(), 2, "No onions");
        CartResponse cart = cartService.addItemToCart(principalCustomer1, request);

        assertEquals(1, cart.items().size());
        CartResponse.CartItemResponse item = cart.items().get(0);
        assertEquals("Gourmet Burger", item.itemName());
        assertEquals(new BigDecimal("200.00"), item.unitPrice());
        assertEquals(2, item.quantity());
        assertEquals(new BigDecimal("400.00"), item.lineTotal());
        assertEquals("No onions", item.specialInstructions());
        assertEquals(new BigDecimal("400.00"), cart.subtotal());
    }

    @Test
    @DisplayName("Adding variant and add-ons calculates full variant base plus modifiers")
    void testAddVariantAndAddOns() {
        // Large Burger (280.00) + Extra Cheddar (30.00) + Bacon (50.00) = 360.00 unit price * 2 = 720.00 line total
        AddCartItemRequest request = new AddCartItemRequest(
                burger.getId(),
                largeBurgerVariant.getId(),
                List.of(extraCheese.getId(), bacon.getId()),
                2,
                null
        );

        CartResponse cart = cartService.addItemToCart(principalCustomer1, request);
        CartResponse.CartItemResponse item = cart.items().get(0);

        assertEquals("Large Double Patty", item.variantName());
        assertEquals(new BigDecimal("360.00"), item.unitPrice());
        assertEquals(2, item.quantity());
        assertEquals(new BigDecimal("720.00"), item.lineTotal());
        assertEquals(new BigDecimal("720.00"), cart.subtotal());
        assertEquals(2, item.selectedOptions().size());
    }

    @Test
    @DisplayName("Adding identical item, variant, and options merges quantity into single line")
    void testDuplicateItemMergesLine() {
        AddCartItemRequest req1 = new AddCartItemRequest(burger.getId(), largeBurgerVariant.getId(), List.of(extraCheese.getId()), 1, "Well done");
        cartService.addItemToCart(principalCustomer1, req1);

        AddCartItemRequest req2 = new AddCartItemRequest(burger.getId(), largeBurgerVariant.getId(), List.of(extraCheese.getId()), 2, "Well done");
        CartResponse cart = cartService.addItemToCart(principalCustomer1, req2);

        assertEquals(1, cart.items().size(), "Identical configuration should merge into single cart line");
        assertEquals(3, cart.items().get(0).quantity());
        assertEquals(3, cart.totalItemCount());
    }

    @Test
    @DisplayName("Updating quantity recalculates subtotal and line totals")
    void testUpdateQuantity() {
        AddCartItemRequest request = new AddCartItemRequest(burger.getId(), null, List.of(), 1, null);
        CartResponse cart = cartService.addItemToCart(principalCustomer1, request);
        UUID itemId = cart.items().get(0).cartItemId();

        CartResponse updatedCart = cartService.updateItemQuantity(principalCustomer1, itemId, new UpdateCartItemQuantityRequest(4));
        assertEquals(4, updatedCart.items().get(0).quantity());
        assertEquals(new BigDecimal("800.00"), updatedCart.subtotal());
    }

    @Test
    @DisplayName("Removing item and clearing cart cleans up line entries")
    void testRemoveAndClearCart() {
        AddCartItemRequest request = new AddCartItemRequest(burger.getId(), null, List.of(), 1, null);
        CartResponse cart = cartService.addItemToCart(principalCustomer1, request);
        UUID itemId = cart.items().get(0).cartItemId();

        CartResponse afterRemove = cartService.removeItemFromCart(principalCustomer1, itemId);
        assertEquals(0, afterRemove.items().size());
        assertEquals(BigDecimal.ZERO.setScale(2), afterRemove.subtotal());

        // Test clear
        cartService.addItemToCart(principalCustomer1, request);
        CartResponse cleared = cartService.clearCart(principalCustomer1);
        assertEquals(0, cleared.items().size());
    }

    @Test
    @DisplayName("Cross-customer cart isolation: Customer 2 cannot see Customer 1's cart")
    void testCrossCustomerCartIsolation() {
        cartService.addItemToCart(principalCustomer1, new AddCartItemRequest(burger.getId(), null, List.of(), 2, null));

        CartResponse customer2Cart = cartService.getOrCreateCart(principalCustomer2);
        assertEquals(0, customer2Cart.items().size(), "Customer 2 should have independent empty cart");
    }

    @Test
    @DisplayName("Cross-tenant item addition is strictly rejected with NOT_FOUND")
    void testCrossTenantItemRejected() {
        // Attempt to add Item from Tenant A into a principal session from Tenant B
        CustomerSession sessionB = new CustomerSession();
        sessionB.setTenantId(tenantB);
        sessionB.setTableNumber("T-B1");
        sessionB.setCustomerName("Tenant B Diner");
        sessionB.setMaskedMobile("+91****99");
        sessionB.setSessionTokenHash(UUID.randomUUID().toString());
        sessionB.setStatus("ACTIVE");
        sessionB.setExpiresAt(Instant.now().plusSeconds(3600));
        sessionRepository.save(sessionB);

        DineFlowPrincipal principalB = new DineFlowPrincipal(sessionB.getId(), tenantB, null, "CUSTOMER_GUEST", List.of("CUSTOMER_MENU_VIEW"), "GUEST");

        assertThrows(BusinessException.class, () ->
                cartService.addItemToCart(principalB, new AddCartItemRequest(burger.getId(), null, List.of(), 1, null))
        );
    }

    @Test
    @DisplayName("Inactive or sold out menu items are rejected from cart addition")
    void testSoldOutItemRejected() {
        burger.setIsAvailable(false);
        menuItemRepository.save(burger);

        assertThrows(BusinessException.class, () ->
                cartService.addItemToCart(principalCustomer1, new AddCartItemRequest(burger.getId(), null, List.of(), 1, null))
        );
    }
}
