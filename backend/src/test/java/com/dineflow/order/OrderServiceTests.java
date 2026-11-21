package com.dineflow.order;

import com.dineflow.branch.domain.Branch;
import com.dineflow.branch.repository.BranchRepository;
import com.dineflow.cart.domain.CustomerCart;
import com.dineflow.cart.domain.CustomerCartItem;
import com.dineflow.cart.repository.CustomerCartRepository;
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
import com.dineflow.order.domain.OrderStatus;
import com.dineflow.order.domain.RestaurantOrder;
import com.dineflow.order.domain.RestaurantOrderItem;
import com.dineflow.order.dto.CreateOrderRequest;
import com.dineflow.order.dto.OrderResponse;
import com.dineflow.order.repository.RestaurantOrderRepository;
import com.dineflow.order.service.OrderService;
import com.dineflow.security.DineFlowPrincipal;
import com.dineflow.tenant.domain.HotelProfile;
import com.dineflow.tenant.domain.Tenant;
import com.dineflow.tenant.repository.HotelProfileRepository;
import com.dineflow.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
public class OrderServiceTests {

    @Autowired
    private OrderService orderService;

    @Autowired
    private RestaurantOrderRepository orderRepository;

    @Autowired
    private CustomerCartRepository cartRepository;

    @Autowired
    private CustomerSessionRepository sessionRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private HotelProfileRepository hotelProfileRepository;

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

    private Tenant tenant1;
    private Tenant tenant2;
    private Branch branch1;
    private Branch branch2;
    private CustomerSession session1;
    private DineFlowPrincipal principal1;
    private MenuItem itemBurger;
    private MenuVariant variantLarge;
    private MenuOption optCheese;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        cartRepository.deleteAll();
        sessionRepository.deleteAll();
        branchConfigRepository.deleteAll();
        menuItemRepository.deleteAll();
        optionRepository.deleteAll();
        optionGroupRepository.deleteAll();
        variantRepository.deleteAll();
        categoryRepository.deleteAll();
        hotelProfileRepository.deleteAll();
        branchRepository.deleteAll();
        tenantRepository.deleteAll();

        // 1. Tenants
        tenant1 = new Tenant();
        tenant1.setSlug("bistro-one");
        tenant1.setBusinessName("Bistro One");
        tenant1.setStatus("ACTIVE");
        tenant1.setSubscriptionPlan("STANDARD");
        tenant1 = tenantRepository.save(tenant1);

        tenant2 = new Tenant();
        tenant2.setSlug("hotel-two");
        tenant2.setBusinessName("Hotel Two");
        tenant2.setStatus("ACTIVE");
        tenant2.setSubscriptionPlan("STANDARD");
        tenant2 = tenantRepository.save(tenant2);

        // 2. Profile for Tenant 1: 5% Tax, 10% Service Charge
        HotelProfile profile1 = new HotelProfile();
        profile1.setTenantId(tenant1.getId());
        profile1.setContactEmail("admin@bistroone.com");
        profile1.setCurrencyCode("INR");
        profile1.setTaxPercentage(new BigDecimal("5.00"));
        profile1.setServiceChargePercentage(new BigDecimal("10.00"));
        hotelProfileRepository.save(profile1);

        // 3. Branches
        branch1 = new Branch("Main Branch", "MB01", "123 Main St", "99999", true, true);
        branch1.setTenantId(tenant1.getId());
        branch1 = branchRepository.save(branch1);

        branch2 = new Branch("Second Branch", "SB02", "456 Side St", "88888", true, false);
        branch2.setTenantId(tenant1.getId());
        branch2 = branchRepository.save(branch2);

        // 4. Session for Tenant 1, Branch 1, Table 07
        session1 = new CustomerSession();
        session1.setTenantId(tenant1.getId());
        session1.setBranchId(branch1.getId());
        session1.setTableNumber("T-07");
        session1.setCustomerName("Jane Doe");
        session1.setMaskedMobile("+91 98****3210");
        session1.setSessionTokenHash(UUID.randomUUID().toString());
        session1.setStatus("ACTIVE");
        session1.setExpiresAt(Instant.now().plus(4, ChronoUnit.HOURS));
        session1 = sessionRepository.save(session1);

        principal1 = new DineFlowPrincipal(
                session1.getId(),
                tenant1.getId(),
                branch1.getId(),
                "CUSTOMER_GUEST",
                List.of("CUSTOMER_MENU_VIEW", "CUSTOMER_ORDER_VIEW_OWN", "CUSTOMER_ORDER_CREATE"),
                "GUEST"
        );

        // 5. Menu Setup
        MenuCategory cat = new MenuCategory("Mains", "Main dishes", 1, true, "burger", null);
        cat.setTenantId(tenant1.getId());
        cat = categoryRepository.save(cat);

        itemBurger = new MenuItem(cat, "Signature Burger", "Gourmet grilled patty", "Detailed grilled patty",
                new BigDecimal("100.00"), DietaryClassification.NON_VEGETARIAN, 15, true, true, 1);
        itemBurger.setTenantId(tenant1.getId());
        itemBurger = menuItemRepository.save(itemBurger);

        variantLarge = new MenuVariant(itemBurger, "Large", "Large portion", new BigDecimal("150.00"), 1, true, true);
        variantLarge.setTenantId(tenant1.getId());
        variantLarge = variantRepository.save(variantLarge);

        MenuOptionGroup group = new MenuOptionGroup("Extras", "Add-on extras", 0, 3, false, 1, true);
        group.setTenantId(tenant1.getId());
        group = optionGroupRepository.save(group);
        itemBurger.getOptionGroups().add(group);
        menuItemRepository.save(itemBurger);

        optCheese = new MenuOption(group, "Extra Cheddar", "Cheddar slice", new BigDecimal("25.00"), 1, true, true);
        optCheese.setTenantId(tenant1.getId());
        optCheese = optionRepository.save(optCheese);
    }

    @Test
    @Transactional
    @DisplayName("Successfully creates order from valid cart with variant, options, taxes, and service charges")
    void createOrderFromCart_validCart_createsOrderWithAuthoritativePricing() {
        // Arrange cart
        CustomerCart cart = new CustomerCart(tenant1.getId(), branch1.getId(), session1.getId(), "T-07");
        CustomerCartItem cartItem = new CustomerCartItem(cart, itemBurger, variantLarge, 2, "Extra crispy please");
        cartItem.setSelectedOptions(Set.of(optCheese));
        cart.addItem(cartItem);
        cartRepository.save(cart);

        // Expected calculation:
        // Variant = 150.00, Option = 25.00 -> Unit = 175.00
        // Qty = 2 -> Subtotal = 350.00
        // Tax 5% = 17.50
        // Service Charge 10% = 35.00
        // Grand Total = 402.50

        CreateOrderRequest request = new CreateOrderRequest("Table near window", new BigDecimal("402.50"));

        // Act
        OrderResponse response = orderService.createOrderFromCart(principal1, "idemp-key-101", request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isNotNull();
        assertThat(response.orderNumber()).startsWith("ORD-");
        assertThat(response.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(response.tableNumber()).isEqualTo("T-07");
        assertThat(response.currencyCode()).isEqualTo("INR");
        assertThat(response.subtotal()).isEqualByComparingTo("350.00");
        assertThat(response.taxAmount()).isEqualByComparingTo("17.50");
        assertThat(response.serviceChargeAmount()).isEqualByComparingTo("35.00");
        assertThat(response.grandTotal()).isEqualByComparingTo("402.50");
        assertThat(response.items()).hasSize(1);

        OrderResponse.OrderItemResponse itemResp = response.items().get(0);
        assertThat(itemResp.itemName()).isEqualTo("Signature Burger");
        assertThat(itemResp.variantName()).isEqualTo("Large");
        assertThat(itemResp.effectiveUnitPrice()).isEqualByComparingTo("175.00");
        assertThat(itemResp.lineTotal()).isEqualByComparingTo("350.00");
        assertThat(itemResp.options()).hasSize(1);
        assertThat(itemResp.options().get(0).optionName()).isEqualTo("Extra Cheddar");
        assertThat(itemResp.options().get(0).priceContribution()).isEqualByComparingTo("25.00");

        // Verify Cart has been cleared
        CustomerCart updatedCart = cartRepository.findByCustomerSessionIdAndTenantId(session1.getId(), tenant1.getId()).orElseThrow();
        assertThat(updatedCart.getItems()).isEmpty();

        // Verify Order persisted in database
        RestaurantOrder persistedOrder = orderRepository.findById(response.orderId()).orElseThrow();
        assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.PLACED);
        assertThat(persistedOrder.getItems()).hasSize(1);
        assertThat(persistedOrder.getStatusHistory()).hasSize(1);
        assertThat(persistedOrder.getStatusHistory().get(0).getNewStatus()).isEqualTo("PLACED");
    }

    @Test
    @DisplayName("Rejects order submission when cart is empty")
    void createOrderFromCart_emptyCart_throwsBusinessException() {
        CustomerCart cart = new CustomerCart(tenant1.getId(), branch1.getId(), session1.getId(), "T-07");
        cartRepository.save(cart);

        CreateOrderRequest request = new CreateOrderRequest(null, null);

        assertThatThrownBy(() -> orderService.createOrderFromCart(principal1, "idemp-empty", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("empty cart");
    }

    @Test
    @Transactional
    @DisplayName("Rejects order when menu item has become inactive or sold out")
    void createOrderFromCart_unavailableItem_throwsBusinessException() {
        CustomerCart cart = new CustomerCart(tenant1.getId(), branch1.getId(), session1.getId(), "T-07");
        CustomerCartItem cartItem = new CustomerCartItem(cart, itemBurger, null, 1, null);
        cart.addItem(cartItem);
        cartRepository.save(cart);

        // Make item sold out
        MenuItem itemToUpdate = menuItemRepository.findById(itemBurger.getId()).orElseThrow();
        itemToUpdate.setIsAvailable(false);
        menuItemRepository.save(itemToUpdate);

        CreateOrderRequest request = new CreateOrderRequest(null, null);

        assertThatThrownBy(() -> orderService.createOrderFromCart(principal1, "idemp-unavail", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sold out");

        // Ensure cart was not cleared on failure
        CustomerCart currentCart = cartRepository.findByCustomerSessionIdAndTenantId(session1.getId(), tenant1.getId()).orElseThrow();
        assertThat(currentCart.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("Rejects order if client expected price does not match server authoritative recalculation")
    void createOrderFromCart_priceChanged_rejectsSubmissionWithConflict() {
        CustomerCart cart = new CustomerCart(tenant1.getId(), branch1.getId(), session1.getId(), "T-07");
        CustomerCartItem cartItem = new CustomerCartItem(cart, itemBurger, null, 1, null);
        cart.addItem(cartItem);
        cartRepository.save(cart);

        // Item base price = 100.00. Tax 5% = 5.00, SC 10% = 10.00 -> Total = 115.00
        // Client expects outdated total 100.00
        CreateOrderRequest request = new CreateOrderRequest(null, new BigDecimal("100.00"));

        assertThatThrownBy(() -> orderService.createOrderFromCart(principal1, "idemp-price-diff", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Order total has changed");
    }

    @Test
    @DisplayName("Idempotency: Re-submitting with identical key returns cached original order without creating duplicate")
    void createOrderFromCart_idempotentRetry_returnsExistingOrder() {
        CustomerCart cart = new CustomerCart(tenant1.getId(), branch1.getId(), session1.getId(), "T-07");
        CustomerCartItem cartItem = new CustomerCartItem(cart, itemBurger, null, 1, null);
        cart.addItem(cartItem);
        cartRepository.save(cart);

        CreateOrderRequest request = new CreateOrderRequest("No onions", new BigDecimal("115.00"));

        // 1st Call
        OrderResponse response1 = orderService.createOrderFromCart(principal1, "idemp-test-duplicate", request);
        assertThat(response1).isNotNull();

        // 2nd Call with identical key and request
        OrderResponse response2 = orderService.createOrderFromCart(principal1, "idemp-test-duplicate", request);
        assertThat(response2).isNotNull();
        assertThat(response2.orderId()).isEqualTo(response1.orderId());
        assertThat(response2.orderNumber()).isEqualTo(response1.orderNumber());

        // Total orders in DB must be exactly 1
        long totalOrders = orderRepository.count();
        assertThat(totalOrders).isEqualTo(1);
    }

    @Test
    @DisplayName("Idempotency: Reusing same key with DIFFERENT request payload is rejected with 409 Conflict")
    void createOrderFromCart_reusedKeyWithDifferentPayload_rejected() {
        CustomerCart cart = new CustomerCart(tenant1.getId(), branch1.getId(), session1.getId(), "T-07");
        CustomerCartItem cartItem = new CustomerCartItem(cart, itemBurger, null, 1, null);
        cart.addItem(cartItem);
        cartRepository.save(cart);

        CreateOrderRequest request1 = new CreateOrderRequest("No onions", new BigDecimal("115.00"));
        orderService.createOrderFromCart(principal1, "idemp-key-diff-payload", request1);

        CreateOrderRequest request2 = new CreateOrderRequest("Different instructions", new BigDecimal("115.00"));

        assertThatThrownBy(() -> orderService.createOrderFromCart(principal1, "idemp-key-diff-payload", request2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("different request payload");
    }

    @Test
    @DisplayName("Historical snapshot immutability: Order items retain original price when menu item is later modified")
    void createOrderFromCart_historicalSnapshot_unaffectedByLaterMenuEdits() {
        CustomerCart cart = new CustomerCart(tenant1.getId(), branch1.getId(), session1.getId(), "T-07");
        CustomerCartItem cartItem = new CustomerCartItem(cart, itemBurger, null, 1, null);
        cart.addItem(cartItem);
        cartRepository.save(cart);

        OrderResponse orderResp = orderService.createOrderFromCart(principal1, "idemp-history-1",
                new CreateOrderRequest(null, new BigDecimal("115.00")));

        // Administrator later changes item name and doubles the price
        MenuItem itemToUpdate = menuItemRepository.findById(itemBurger.getId()).orElseThrow();
        itemToUpdate.setName("Super Deluxe Wagyu Burger");
        itemToUpdate.setBasePrice(new BigDecimal("999.00"));
        menuItemRepository.save(itemToUpdate);

        // Fetch order again
        OrderResponse fetchedOrder = orderService.getCustomerOrder(principal1, orderResp.orderId());

        // Historical snapshots must preserve the historical values
        assertThat(fetchedOrder.grandTotal()).isEqualByComparingTo("115.00");
        assertThat(fetchedOrder.items().get(0).itemName()).isEqualTo("Signature Burger");
        assertThat(fetchedOrder.items().get(0).baseUnitPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Session Isolation: Customer cannot view another guest session's order")
    void getCustomerOrder_otherCustomerSession_throwsAccessDenied() {
        CustomerCart cart = new CustomerCart(tenant1.getId(), branch1.getId(), session1.getId(), "T-07");
        CustomerCartItem cartItem = new CustomerCartItem(cart, itemBurger, null, 1, null);
        cart.addItem(cartItem);
        cartRepository.save(cart);

        OrderResponse orderResp = orderService.createOrderFromCart(principal1, "idemp-session-iso", null);

        // Create a different customer session
        CustomerSession sessionOther = new CustomerSession();
        sessionOther.setTenantId(tenant1.getId());
        sessionOther.setBranchId(branch1.getId());
        sessionOther.setTableNumber("T-09");
        sessionOther.setCustomerName("Bob Other");
        sessionOther.setMaskedMobile("+91 99****8888");
        sessionOther.setSessionTokenHash(UUID.randomUUID().toString());
        sessionOther.setStatus("ACTIVE");
        sessionOther.setExpiresAt(Instant.now().plus(4, ChronoUnit.HOURS));
        sessionOther = sessionRepository.save(sessionOther);

        DineFlowPrincipal otherPrincipal = new DineFlowPrincipal(
                sessionOther.getId(),
                tenant1.getId(),
                branch1.getId(),
                "CUSTOMER_GUEST",
                List.of("CUSTOMER_MENU_VIEW", "CUSTOMER_ORDER_VIEW_OWN", "CUSTOMER_ORDER_CREATE"),
                "GUEST"
        );

        assertThatThrownBy(() -> orderService.getCustomerOrder(otherPrincipal, orderResp.orderId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("You do not own this order");
    }

    @Test
    @DisplayName("Cross-tenant isolation: Cannot create order in different tenant")
    void createOrderFromCart_crossTenant_throwsForbidden() {
        CustomerCart cart = new CustomerCart(tenant1.getId(), branch1.getId(), session1.getId(), "T-07");
        CustomerCartItem cartItem = new CustomerCartItem(cart, itemBurger, null, 1, null);
        cart.addItem(cartItem);
        cartRepository.save(cart);

        // Principal claiming tenant 2 but customer session belongs to tenant 1
        DineFlowPrincipal crossTenantPrincipal = new DineFlowPrincipal(
                session1.getId(),
                tenant2.getId(), // Tenant 2
                branch1.getId(),
                "CUSTOMER_GUEST",
                List.of("CUSTOMER_ORDER_CREATE"),
                "GUEST"
        );

        assertThatThrownBy(() -> orderService.createOrderFromCart(crossTenantPrincipal, "idemp-cross", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cross-tenant session access forbidden");
    }
}
