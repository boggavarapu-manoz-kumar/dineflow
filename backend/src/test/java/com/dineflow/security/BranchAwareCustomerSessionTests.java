package com.dineflow.security;

import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
import com.dineflow.identity.dto.CustomerSessionRequest;
import com.dineflow.identity.dto.CustomerSessionResponse;
import com.dineflow.identity.service.AuthService;
import com.dineflow.menu.domain.DietaryClassification;
import com.dineflow.menu.domain.MenuCategory;
import com.dineflow.menu.domain.MenuItem;
import com.dineflow.menu.domain.MenuItemBranchConfiguration;
import com.dineflow.menu.dto.CustomerMenuResponse;
import com.dineflow.menu.repository.MenuCategoryRepository;
import com.dineflow.menu.repository.MenuItemBranchConfigurationRepository;
import com.dineflow.menu.repository.MenuItemRepository;
import com.dineflow.menu.service.CustomerMenuService;
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
public class BranchAwareCustomerSessionTests {

    @Autowired
    private HmacEntryTokenVerifier hmacVerifier;

    @Autowired
    private AuthService authService;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private MenuCategoryRepository categoryRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private MenuItemBranchConfigurationRepository branchConfigRepository;

    @Autowired
    private CustomerMenuService customerMenuService;

    private UUID tenantA;
    private UUID tenantB;
    private Branch branchA;
    private Branch inactiveBranchA;
    private Branch branchB;

    @BeforeEach
    void setUp() {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();

        branchA = new Branch("Downtown Branch", "DT-01", "123 Main St", "+919876543210", true, true);
        branchA.setTenantId(tenantA);
        branchRepository.save(branchA);

        inactiveBranchA = new Branch("Old Closed Branch", "CL-01", "456 Old St", "+919876543211", false, false);
        inactiveBranchA.setTenantId(tenantA);
        branchRepository.save(inactiveBranchA);

        branchB = new Branch("Suburbs Branch", "SB-01", "789 Park St", "+919876543212", true, true);
        branchB.setTenantId(tenantB);
        branchRepository.save(branchB);
    }

    @Test
    @DisplayName("Valid 5-part QR token binds tenant, branch, and table number into CustomerSession")
    void testValidBranchAwareQrSession() {
        String token = hmacVerifier.createSignedToken(tenantA, branchA.getId(), "Table-05", Instant.now().getEpochSecond());

        CustomerSessionRequest request = new CustomerSessionRequest("Alice", "+919876543210", token);
        CustomerSessionResponse response = authService.createCustomerSession(request);

        assertNotNull(response.guestToken());
        assertEquals(tenantA, response.tenantId());
        assertEquals(branchA.getId(), response.branchId());
        assertEquals("Table-05", response.tableNumber());
    }

    @Test
    @DisplayName("Invalid QR signature is rejected")
    void testInvalidSignatureRejected() {
        String validToken = hmacVerifier.createSignedToken(tenantA, branchA.getId(), "Table-05", Instant.now().getEpochSecond());
        String tamperedToken = validToken.substring(0, validToken.length() - 4) + "XXXX";

        CustomerSessionRequest request = new CustomerSessionRequest("Hacker", "+919876543210", tamperedToken);
        assertThrows(RuntimeException.class, () -> authService.createCustomerSession(request));
    }

    @Test
    @DisplayName("Expired QR token is rejected")
    void testExpiredQrTokenRejected() {
        long expiredTimestamp = Instant.now().getEpochSecond() - 90000; // Older than maxAge (86400s)
        String token = hmacVerifier.createSignedToken(tenantA, branchA.getId(), "Table-05", expiredTimestamp);

        CustomerSessionRequest request = new CustomerSessionRequest("Late Guest", "+919876543210", token);
        assertThrows(RuntimeException.class, () -> authService.createCustomerSession(request));
    }

    @Test
    @DisplayName("Branch belonging to Tenant B cannot be used in QR token for Tenant A")
    void testCrossTenantBranchMismatchRejected() {
        // Sign token claiming Tenant A but with Branch B's ID
        String token = hmacVerifier.createSignedToken(tenantA, branchB.getId(), "Table-05", Instant.now().getEpochSecond());

        CustomerSessionRequest request = new CustomerSessionRequest("Attacker", "+919876543210", token);
        assertThrows(RuntimeException.class, () -> authService.createCustomerSession(request));
    }

    @Test
    @DisplayName("QR token referencing an inactive or disabled branch is rejected")
    void testInactiveBranchRejected() {
        String token = hmacVerifier.createSignedToken(tenantA, inactiveBranchA.getId(), "Table-05", Instant.now().getEpochSecond());

        CustomerSessionRequest request = new CustomerSessionRequest("Guest", "+919876543210", token);
        assertThrows(RuntimeException.class, () -> authService.createCustomerSession(request));
    }

    @Test
    @DisplayName("Branch-specific pricing and availability overrides are reflected in customer menu")
    void testBranchSpecificMenuOverrides() {
        MenuCategory category = new MenuCategory("Italian Specials", null, 0, true, null, null);
        category.setTenantId(tenantA);
        categoryRepository.save(category);

        MenuItem pizza = new MenuItem(category, "Woodfired Pizza", "Classic woodfired", null,
                new BigDecimal("300.00"), DietaryClassification.VEGETARIAN, 15, true, true, 0);
        pizza.setTenantId(tenantA);
        menuItemRepository.save(pizza);

        MenuItem pasta = new MenuItem(category, "Alfredo Pasta", "Creamy Alfredo", null,
                new BigDecimal("250.00"), DietaryClassification.VEGETARIAN, 15, true, true, 1);
        pasta.setTenantId(tenantA);
        menuItemRepository.save(pasta);

        // Branch A overrides Woodfired Pizza price to 350.00 and marks Alfredo Pasta unavailable
        MenuItemBranchConfiguration pizzaConfig = new MenuItemBranchConfiguration(branchA, pizza, new BigDecimal("350.00"), true, true);
        pizzaConfig.setTenantId(tenantA);
        branchConfigRepository.save(pizzaConfig);

        MenuItemBranchConfiguration pastaConfig = new MenuItemBranchConfiguration(branchA, pasta, null, false, true);
        pastaConfig.setTenantId(tenantA);
        branchConfigRepository.save(pastaConfig);

        // Verify with session established at Branch A
        String token = hmacVerifier.createSignedToken(tenantA, branchA.getId(), "Table-01", Instant.now().getEpochSecond());
        CustomerSessionResponse sessionResp = authService.createCustomerSession(new CustomerSessionRequest("Diner", "+919876543210", token));

        DineFlowPrincipal principal = new DineFlowPrincipal(
                sessionResp.sessionId(),
                tenantA,
                branchA.getId(),
                "CUSTOMER_GUEST",
                List.of("CUSTOMER_MENU_VIEW"),
                "GUEST"
        );

        CustomerMenuResponse menu = customerMenuService.getMenuForGuest(principal);
        assertNotNull(menu);

        CustomerMenuResponse.CustomerCategoryResponse catResp = menu.categories().get(0);
        assertEquals(2, catResp.items().size());

        CustomerMenuResponse.CustomerMenuItemResponse pizzaItem = catResp.items().stream()
                .filter(i -> i.name().equals("Woodfired Pizza"))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("350.00"), pizzaItem.effectivePrice());
        assertTrue(pizzaItem.isBranchPriceOverridden());
        assertTrue(pizzaItem.isAvailable());

        CustomerMenuResponse.CustomerMenuItemResponse pastaItem = catResp.items().stream()
                .filter(i -> i.name().equals("Alfredo Pasta"))
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("250.00"), pastaItem.effectivePrice());
        assertFalse(pastaItem.isBranchPriceOverridden());
        assertFalse(pastaItem.isAvailable()); // Branch marked it unavailable
    }
}
